# La voix, les vidéos et les campagnes — la référence

Ce fichier remet à plat ce qui a été décidé. Il est écrit à partir du cahier des charges du propriétaire ; la colonne « état » dit ce qui existe, ce qui est partiel et ce qui reste à faire (mise à jour à chaque étape). Rien de ce qui est marqué « fait » n'a été essayé sur un vrai téléphone sauf mention contraire.

## 0. Le principe

On parle avec Claude. Les **instructions générales** sont lancées au premier message envoyé à une conversation par le widget (consignes du dépôt, du mode, de la qualité). Tout ce qui est dit part vers une session Claude : le téléphone ne produit rien lui-même.

## 1. La voix

### 1.1 Le bouton micro = le mode agent

Un **parseur** (sur le téléphone, sans réseau) reçoit ce qui est dit.

| Ce qui est dit | Ce qui se passe |
|---|---|
| commence par **« instruction »** | Instruction *pastilles*. Il ne reconnaît QUE les pastilles **session** et **repo** : il cherche leurs noms dans les listes, change les pastilles, puis **envoie obligatoirement à la session Claude de la pastille** l'information pastille (repo / session). **Avant**, il confirme oralement les deux (session et repo) et attend une **validation** ou une **correction**. |
| tout le reste (pas de « instruction », ou rien de reconnu comme pastille) | Une **commande** : envoyée telle quelle à la session Claude en cours. |

Le parseur ne reconnaît plus Push, modèle, effort, qualité, déploiement : **tout cela est manuel**.

### 1.2 Le super agent

Une écoute **permanente** (même écran verrouillé), en deux étages :

1. **arrière-plan** : un petit modèle local (Vosk) + les mots de réveil, faible consommation ;
2. **le canal Claude Code superviseur** : une session de supervision qui peut parler **directement à toutes les sessions connues**.

Il s'enclenche **par le bouton**, ou en disant **son nom** à l'agent (si le super agent n'est pas paramétré, un signal « paramétrez votre super agent » est envoyé). **Tant que le super agent est enclenché, le bouton micro est grisé.**

Avec une **clé Anthropic** (Réglages), on parle en langage très naturel directement au super agent, et la clé **remplace le parseur**.

Quand le super agent reçoit une commande :

- il reconnaît naturellement la **production** demandée : docs, vidéo, page, app, contrat Aiwa ;
- une commande spéciale, le **Cast** : envoyer à la TV la vidéo existante, ou, si une production vidéo est demandée dans la même commande, la vidéo qui va venir — le cast est alors lancé tout seul à la réception de la nouvelle vidéo ;
- il peut envoyer aux sessions des **instructions pastilles** (comme un agent multiple), et ces instructions peuvent aussi agir sur les **pastilles du wallet** ;
- **avant de lancer quoi que ce soit**, il confirme oralement les instructions et un **résumé très succinct** de la production demandée, et attend une **validation** ou une **correction** ; il confirme aussi les tâches **multi-sessions**.

Il **ne fait jamais rien lui-même** : c'est un intermédiaire entre les sessions, ou simplement un interlocuteur (on peut lui demander d'intégrer des corpus à son propre contexte, pas d'intervenir directement où que ce soit).

### 1.3 Stop et Éteins

- **« stop »** arrête la production de l'agent ou du super agent ; il demande aussi à la ou aux **dernières sessions de son intermédiation en cours d'oublier son dernier commentaire**.
- **« éteins »** demande au super agent de s'éteindre : **plus d'écoute permanente**. Le petit modèle d'arrière-plan (écran verrouillé) doit lui aussi reconnaître « éteins ».

### 1.4 Les onze reconnaissances personnalisables

Pour l'agent et le super agent. **À chaque changement, l'information est transmise au petit modèle, à l'agent et au super agent.**

1. **Réveil**
2. **Super agent**
3. **Instruction**
4. **Pastille session**
5. **Pastille repo**
6. **Validation**
7. **Stop**
8. **Éteins**
9. **Demande de renvoi** des instructions générales et des instructions pastilles
10. **T'es où là** : le téléphone lit à voix haute les pastilles du widget (dépôt, session, modèle, push)
11. **Fais ton rapport** : (super agent ouvert) son rapport d'activité depuis le dernier message, et une idée

### 1.5 Les annonces

- À chaque **nouvelle production captée**, on entend « **c'est prêt** » — si c'est activé dans les Réglages.
- Quand le super agent est **paramétré mais l'écoute désactivée** : il parle d'abord pour demander « **je suis disponible ?** » ; si on répond oui, il fait un **rapport de toute l'activité depuis le dernier message qu'on lui a envoyé** et **propose une idée**.

## 2. Les vidéos

La fenêtre Vidéos :

- **cocher** des documents du dossier `docs/` — ou non ;
- le **choix de la qualité** ;
- l'**aperçu de la dernière production** (un appui la lit), avec les boutons **cast**, **download**, **share** ;
- **voir le dossier des vidéos** (`docs/videos/`, un sous-dossier de `docs/`).

Documents : possibilité d'**envoyer** des fichiers et des vidéos **du téléphone vers GitHub** (dans `docs/`).

## 3. Les campagnes

Une campagne avance **cible par cible, sans validation de liste** : pour chaque cible, la session fabrique ce qui est demandé (vidéo, documents, page), pousse, ajoute sa proposition, puis passe à la suivante, jusqu'à l'arrêt (ou 30 cibles). Les propositions arrivent **une par une** et on valide **la cible et sa production ensemble**, dans l'appli.

Un **onglet par campagne** et un onglet **Nouvelle campagne**.

### 3.1 Nouvelle campagne (le formulaire)

- nom de la campagne ;
- choisir une **session** et un **repo** ;
- cocher la **liste de documents** du dossier documents ;
- cocher **test** ;
- cocher la **liste de repos GitHub** ;
- cocher les **créations à produire, personnalisées** pour chaque cible : **vidéo, documents, page** (une seule page web avec son arborescence : un lien personnalisé par cible) ;
- cocher si on utilise **app, aiwa** du repo, tels quels : l'APK et l'app Aiwa **ne se personnalisent pas** ;
- **prompt** texte ;
- **Valider** : envoi, et le formulaire **devient l'onglet de la campagne**.

### 3.2 Un onglet campagne

- **voir le dossier** (sous-dossier de `docs/`) ;
- la **liste des propositions**, classées par type (WhatsApp, Telegram, Mail, LinkedIn) ; une ligne = les **boutons des modes** existants pour cette campagne (pour voir), le **nom** et le **lien message / profil** (un « super share ») ;
- si **test** avait été coché : seulement les modes et un bouton share **normal** ;
- le bouton **share** ouvre d'abord un **aperçu** de ce qui est envoyé (le message personnalisé et, en plus, les liens docs, vidéo, page, app, aiwa), **avant confirmation** ;
- on ne peut **lancer qu'une proposition à la fois** ;
- **Arrêter les travaux de la campagne** (avec confirmation) : la session est prévenue (`[Aiwa — stop campagne]`) et s'arrête à la fin de son étape en cours (une session cloud ne s'interrompt pas de l'extérieur) ;
- **relancer** : redonne le choix session et repo, test, documents, repos et modes, pour faire une nouvelle campagne.

## 4. État de l'existant (au moment de l'écriture)

| Sujet | État |
|---|---|
| Parseur « instruction » | **fait** : session et repo seulement |
| Confirmation orale avant d'appliquer | **fait** (session + repo, validation ou correction) |
| Info pastille envoyée à la session | **fait** (message tout de suite) |
| Super agent : écoute de réveil + deuxième écoute | existe (mots « mon agent », « mon super agent », « instruction », « stop Claude ») |
| Bouton micro grisé quand le super agent est ouvert | **fait** |
| « Éteins » | **fait** (petit modèle + écoute de l'agent) |
| Stop → faire oublier le dernier commentaire aux sessions | **fait** (`/api/agent/stop`) |
| Onze reconnaissances personnalisables | **fait** (Réglages → « Mes mots » : dire et/ou écrire chaque mot ; transmis au petit modèle, au parseur et au super agent) |
| « T'es où là » (mot 10) et « fais ton rapport » (mot 11) | **fait** ; « t'es où là » par le petit modèle français **pas essayé** |
| Le super agent ouvre une production (documents, vidéo, page, app, aiwa) en langage naturel, relu puis validé | **fait**, jamais essayé à la voix sur un téléphone |
| Détruire le super agent et en recommencer un (Réglages → Super agent) | **fait** (la conversation reste dans claude.ai) |
| « C'est prêt » | **fait** (réglable) |
| « Je suis disponible ? » + rapport + idée | **fait avec une limite** : le téléphone parle et pose une notification « Oui » ; la réponse est un appui (le micro ne peut pas s'ouvrir en arrière-plan) |
| Clé Anthropic qui remplace le parseur | **fait** : avec la clé, Claude lit toute phrase dite au micro (avec ou sans « instruction ») |
| Vidéos : cases documents, aperçu, cast, download, share, dossier | **fait** (fenêtre refaite ; les vidéos vivent dans `docs/videos/` ; la liste est lue avec `git`, l'API de GitHub n'étant qu'un repli ; la taille d'un film n'est plus affichée) |
| Envoi de fichiers du téléphone vers `docs/` | **pas fait** : écrire dans GitHub demande un jeton, et Aiwa n'en demande jamais ; piste : passer par la session (le fichier part dans le message, la session le range) |
| Campagnes en onglets + formulaire | **fait** (un dossier par campagne dans `docs/campagnes/`, onglets, formulaire, propositions par canal, aperçu avant envoi, relancer) |
