package com.aiwa.store
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.aiwa.bridge.BOOTSTRAP_COMMAND
import com.aiwa.bridge.BackendVerdict
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.backendProblemMessage
import kotlinx.coroutines.launch
// One forced backend restart per app process at most: if the checkout
// can't be updated (no network), restarting again would only throw away
// the warm claude process every time for nothing.
private var restartedOutdatedBackend=false
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableDictation(applicationContext);setContent{MaterialTheme{AiwaScreen()}}}}
/**
 * The dictation module's screen (optional: the Store opens it from its Dictate tab): a full-page text field and a send button, nothing else (asked for:
 * "uniquement un champ texte pleine page et envoi"). Everything else lives on the
 * widget: session, model, mic, repository, Push, Deploy, the links, "Claude ↗".
 * A banner at the top says that every function is in the widget, to be put on the
 * home screen. One thin line above the field appears only when there is something to say
 * (backend starting or down, a real error). Opening this screen still does the
 * setup work below: the Termux permission request (only an Activity can show
 * it), starting the backend and the keep-alive service, the version check.
 */
@Composable private fun AiwaScreen(){
val context=LocalContext.current
val bridge=remember{LocalClaudeBridge()}
val scope=rememberCoroutineScope()
val state by AiwaRepository.state.collectAsState()
// Survives Android reclaiming the app: a long text must not vanish with it.
var input by rememberSaveable{mutableStateOf("")}
val focus=remember{FocusRequester()}
fun refreshWidget(){scope.launch{AiwaWidget().updateAll(context)}}
// The field is emptied at once (so it is ready for the next message), and the
// text comes back if it did not go: a full-page field is for long messages.
fun send(){
val text=input
if(text.isBlank())return
input=""
scope.launch{
val sent=sendAndTrack(context,bridge,text,toastErrors=true)
if(!sent)input=if(input.isEmpty())text else text+"\n"+input
refreshWidget()
}
}
// Reported live: "Not allowed to start service Intent ... without
// permission com.termux.permission.RUN_COMMAND" — declaring the
// permission in the manifest was never enough on its own; a dangerous
// permission still needs an actual runtime request, which only an Activity can show — this is why the
// backend auto-launch below lives here rather than directly in a
// widget ActionCallback, which has no such context.
fun launchTermuxBackend(){
val result=startAiwaBackendViaTermux(context)
AiwaRepository.update{
// Reported live: using WORKING here left the "Envoyer" button
// permanently disabled (enabled=status!=WORKING), because nothing
// ever clears it afterward — there's no real signal for "Termux
// actually finished starting the server", only whether the
// launch intent itself was accepted. WORKING must stay reserved
// for an actual in-flight Claude request (see the send button's
// own coroutine below); this only ever reports acceptance, so it
// keeps status READY and lets the user just try sending — a
// connection error there is the real, honest signal either way.
// No "Termux démarré…" message on success: the user asked for no
// informational messages.
if(result.isSuccess)it.copy(status=AiwaState.Status.READY)
else it.copy(status=AiwaState.Status.ERROR,notice="Impossible de lancer Termux : ${result.exceptionOrNull()?.message}")
}
refreshWidget()
}
// Android 13+ shows no notification without this permission, and the card on the
// lock screen is one. Asked after the Termux one, never on top of it.
val notificationPermissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
fun askNotifications(){
if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,"android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)notificationPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
}
val termuxPermissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
if(granted)launchTermuxBackend()
else AiwaRepository.update{it.copy(status=AiwaState.Status.ERROR,notice="Permission Termux refusée — impossible de démarrer le backend automatiquement.")}
askNotifications()
}
fun startTermuxBackend(){
val granted=ContextCompat.checkSelfPermission(context,"com.termux.permission.RUN_COMMAND")==PackageManager.PERMISSION_GRANTED
if(granted){launchTermuxBackend();askNotifications()} else termuxPermissionLauncher.launch("com.termux.permission.RUN_COMMAND")
}
// The always-on listening for "instruction Aiwa" (WakeWordService.kt): off until it is turned on here, which asks for the microphone once.
var listening by remember{mutableStateOf(ListenSettings.enabled(context))}
val listenPermissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
if(granted){ListenSettings.setEnabled(context,true);listening=true;WakeWordService.start(context)}
}
fun turnListening(on:Boolean){
if(!on){ListenSettings.setEnabled(context,false);listening=false;WakeWordService.stop(context);return}
if(ContextCompat.checkSelfPermission(context,"android.permission.RECORD_AUDIO")==PackageManager.PERMISSION_GRANTED){ListenSettings.setEnabled(context,true);listening=true;WakeWordService.start(context)}
else listenPermissionLauncher.launch("android.permission.RECORD_AUDIO")
}
LaunchedEffect(Unit){try{focus.requestFocus()}catch(err:Exception){}}
LaunchedEffect(Unit){
// Reported live: "I have to restart the app to send the first
// message" — a real bug, not just the (separate, expected) ~20-30s
// cold-start wait. If the coroutine below sending a previous message
// got torn down without reaching its own catch block (Activity
// destroyed/recreated by Android while backgrounded, a config change,
// etc.), AiwaRepository's status stayed WORKING forever — a
// process-wide singleton, so it survives the Activity being recreated
// and permanently disables "Envoyer" (enabled=status!=WORKING) until
// the whole app PROCESS dies, which is the only thing that actually
// resets it. A fresh screen appearing is never mid-user-action, so
// any leftover WORKING here is stale by definition — safe to clear.
// Not while this process is itself sending (a widget send goes on after its screen is gone): the backend then
// says what is going on (BackendSync), so the widget's "Envoi en cours…" is not wiped by opening the app.
if(AiwaRepository.state.value.status==AiwaState.Status.WORKING&&!SendTracker.inFlight.get()){
AiwaRepository.update{it.copy(status=AiwaState.Status.READY)}
}
startTermuxBackend()
// Reported live: "je veux que tu implémentes le micro widget sans
// ouvrir l'application dès la première utilisation" — explicitly
// accepted trade-off (see KeepAliveService.kt): a real foreground
// service keeps Aiwa's process resident so the widget's mic doesn't
// need a cold start after the process has been idle a while. Starting
// it here means it's running from the first time the app is opened,
// same bootstrap spot as the Termux auto-start above.
try{ContextCompat.startForegroundService(context,Intent(context,KeepAliveService::class.java))}catch(err:Exception){}
// The listening does not come back by itself after the phone restarts (Android refuses a microphone service started from the background): opening the app does it.
if(ListenSettings.enabled(context)&&WakeWordService.instance==null)WakeWordService.start(context)
// Reported live: features silently missing because the backend
// running on the phone was older than this app (history never
// loaded — it predated /api/history). The backend reports its
// version. startAiwaBackendViaTermux above already pulls and restarts
// it when the checkout changed, so an old version seen right away may
// just be that restart still pending: wait, look again, and only then
// force one restart (at most once per process).
var backendStatus=awaitBackendStatus(bridge,30_000)
if(backendStatus!=null&&backendStatus.version<EXPECTED_BACKEND_VERSION){
// The script started above may be replacing the old server right now: it
// still answers for a few seconds. Wait for the NEW version instead of a
// fixed pause, and only force a restart when it really doesn't come.
val updated=awaitBackendVersion(bridge,EXPECTED_BACKEND_VERSION,45_000)
if(updated!=null){
backendStatus=updated
}else if(!restartedOutdatedBackend){
restartedOutdatedBackend=true
startAiwaBackendViaTermux(context,forceRestart=true)
backendStatus=awaitBackendVersion(bridge,EXPECTED_BACKEND_VERSION,45_000)?:awaitBackendStatus(bridge,10_000)
}
}
BackendSync.refresh(bridge)
val versionNow=AiwaRepository.state.value.backendVersion
if(backendStatus==null){
AiwaRepository.update{it.copy(status=AiwaState.Status.ERROR,notice="Backend injoignable après 30 s. Vérifie que Termux est installé, que allow-external-apps=true est dans ~/.termux/termux.properties et que bootstrap.sh a déjà été lancé une fois. Dans Termux : cat ~/aiwa_start.log ~/aiwa_backend.log")}
}else if(versionNow<EXPECTED_BACKEND_VERSION){
AiwaRepository.update{it.copy(status=AiwaState.Status.ERROR,notice="Backend obsolète (version $versionNow, il faut $EXPECTED_BACKEND_VERSION) et mise à jour automatique impossible. Dans Termux : cd ~/aiwa_store && git pull && pkill -f aiwa_server.py, puis rouvre Aiwa.")}
}
}
// Only what needs saying. A start-up complaint is dropped once the backend answers.
val notice=state.notice?.takeUnless{
(state.backend=="up"&&it.startsWith("Backend injoignable"))||(state.backendVersion>=EXPECTED_BACKEND_VERSION&&it.startsWith("Backend obsolète"))
}
val note=when{
state.backend=="missing"->backendProblemMessage(if(state.backendMissing=="termux")BackendVerdict.TERMUX_MISSING else BackendVerdict.NOT_INSTALLED)
state.backend=="starting"->"⏳ Démarrage du backend (Termux)… 10 à 20 s"
state.backend=="down"->"⚠ Backend arrêté : relance automatique en cours"
else->notice
}
val problem=state.backend=="down"||state.backend=="missing"||(state.backend!="starting"&&notice!=null)
Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
// Asked for: say at the top that every function lives in the widget, to be put on the home screen.
Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.secondaryContainer,modifier=Modifier.fillMaxWidth()){
Text(
"Toutes les fonctions d'Aiwa (sessions, modèle, dépôt, Push, Deploy, micro…) sont dans le widget : ajoute-le à ton écran d'accueil. Cette page sert seulement à écrire un message.",
modifier=Modifier.padding(12.dp),
style=MaterialTheme.typography.bodyMedium,
color=MaterialTheme.colorScheme.onSecondaryContainer,
)
}
if(note!=null)Text(note,style=MaterialTheme.typography.bodySmall,color=if(problem)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
// The backend is not installed in Termux (the micro of the widget sends here): the whole installation, once.
if(state.backend=="missing"){
Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.errorContainer,modifier=Modifier.fillMaxWidth()){
Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
Text("Installer Aiwa en entier, une seule fois",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onErrorContainer)
Text(
"1. Termux doit être sur le téléphone (depuis F-Droid : la version du Play Store est une copie non officielle trop ancienne).\n2. Ouvre Termux, colle cette ligne et valide : elle installe le backend, règle l'autorisation entre Aiwa et Termux, et télécharge la dernière application dans Téléchargements.\n3. Quand elle a fini, reviens ici : le micro du widget marche.",
style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onErrorContainer,
)
SelectionContainer{Text(BOOTSTRAP_COMMAND,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace,color=MaterialTheme.colorScheme.onErrorContainer)}
Button(onClick={context.startActivity(Intent(context,InstallHelpActivity::class.java))},modifier=Modifier.fillMaxWidth()){Text("Copier la ligne et ouvrir Termux")}
}
}
}
// Two things that need a person, each with the way to do it (see ClaudeLoginActivity / HealthActivity).
if(state.claudeLogin=="needed"){
Button(onClick={context.startActivity(Intent(context,ClaudeLoginActivity::class.java))},modifier=Modifier.fillMaxWidth()){Text("Claude n'est pas connecté : connecter")}
}
Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
Column(Modifier.weight(1f)){
Text("Écoute permanente : dis « instruction Aiwa »",style=MaterialTheme.typography.titleSmall)
Text("Le téléphone écoute le mot « instruction » en continu, même écran verrouillé, puis ouvre le micro. Tout reste sur le téléphone. Le point vert du micro reste affiché et la batterie baisse plus vite. 41 Mo à télécharger la première fois.",style=MaterialTheme.typography.bodySmall)
}
Switch(checked=listening,onCheckedChange={turnListening(it)})
}
TextButton(onClick={context.startActivity(Intent(context,HealthActivity::class.java))}){Text("État d'Aiwa (Claude, alertes, permissions)")}
OutlinedTextField(
value=input,
onValueChange={input=it},
modifier=Modifier.weight(1f).fillMaxWidth().focusRequester(focus),
placeholder={Text("Message")},
keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Sentences),
)
Button(
onClick={send()},
enabled=input.isNotBlank()&&state.status!=AiwaState.Status.WORKING,
modifier=Modifier.fillMaxWidth(),
){Text(if(state.status==AiwaState.Status.WORKING)"Envoi…" else "Envoyer")}
}
}
