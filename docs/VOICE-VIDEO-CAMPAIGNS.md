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

### 1.4 Les neuf reconnaissances personnalisables

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

### 1.5 Les annonces

- À chaque **nouvelle production captée**, on entend « **c'est prêt** » — si c'est activé dans les Réglages.
- Quand le super agent est **paramétré mais l'écoute désactivée** : il parle d'abord pour demander « **je suis disponible ?** » ; si on répond oui, il fait un **rapport de toute l'activité depuis le dernier message qu'on lui a envoyé** et **propose une idée**.

## 2. Les vidéos

La fenêtre Vidéos :

- **cocher** des documents du dossier `docs/` — ou non ;
- le **choix de la qualité** ;
- l'**aperçu de la dernière production** (un appui la lit), avec les boutons **cast**, **download**, **share** ;
- **voir le dossier des vidéos** (un sous-dossier de `docs/`).

Documents : possibilité d'**envoyer** des fichiers et des vidéos **du téléphone vers GitHub** (dans `docs/`).

## 3. Les campagnes

Un **onglet par campagne** et un onglet **Nouvelle campagne**.

### 3.1 Nouvelle campagne (le formulaire)

- nom de la campagne ;
- choisir une **session** et un **repo** ;
- cocher la **liste de documents** du dossier documents ;
- cocher **test** ;
- cocher la **liste de repos GitHub** ;
- cocher les **créations à produire, personnalisées** (vidéo, docs) ;
- cocher si on utilise **page, app, aiwa** du repo ;
- **prompt** texte ;
- **Valider** : envoi, et le formulaire **devient l'onglet de la campagne**.

### 3.2 Un onglet campagne

- **voir le dossier** (sous-dossier de `docs/`) ;
- la **liste des propositions**, classées par type (WhatsApp, Telegram, Mail, LinkedIn) ; une ligne = les **boutons des modes** existants pour cette campagne (pour voir), le **nom** et le **lien message / profil** (un « super share ») ;
- si **test** avait été coché : seulement les modes et un bouton share **normal** ;
- le bouton **share** ouvre d'abord un **aperçu** de ce qui est envoyé (le message personnalisé et, en plus, les liens docs, vidéo, page, app, aiwa), **avant confirmation** ;
- on ne peut **lancer qu'une proposition à la fois** ;
- **relancer** : redonne le choix session et repo, test, documents, repos et modes, pour faire une nouvelle campagne.

## 4. État de l'existant (au moment de l'écriture)

| Sujet | État |
|---|---|
| Parseur « instruction » | existe, mais reconnaît aussi modèle, effort, push, qualité, déploiement, TV → à réduire à session et repo |
| Confirmation orale avant d'appliquer | existe (relecture de tout, validation) → à limiter session + repo |
| Info pastille envoyée à la session | partielle (le cerveau la glisse au message suivant) → à envoyer tout de suite |
| Super agent : écoute de réveil + deuxième écoute | existe (mots « mon agent », « mon super agent », « instruction », « stop Claude ») |
| Bouton micro grisé quand le super agent est ouvert | à faire |
| « Éteins » | à faire |
| Stop → faire oublier le dernier commentaire aux sessions | à faire |
| Neuf reconnaissances personnalisables | trois emplacements aujourd'hui (réveil, agent, super agent) |
| « C'est prêt » | à faire |
| « Je suis disponible ? » + rapport + idée | à faire |
| Clé Anthropic qui remplace le parseur | existe en partie (repli quand le parseur échoue) |
| Vidéos : cases documents, aperçu, cast, download, share | fenêtre simple (liste, demande) → à refaire |
| Envoi de fichiers du téléphone vers `docs/` | à faire |
| Campagnes en onglets + formulaire | un seul écran de propositions → à refaire |
