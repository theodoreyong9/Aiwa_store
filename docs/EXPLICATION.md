# Aiwa, expliqué

*Pour tout le monde : pas besoin de connaître la cryptographie. Le protocole formel est dans le [yellow paper](YELLOWPAPER.md) (en anglais) ; cette page dit les mêmes choses en mots simples, et dit ce qui n'est pas résolu. English: [EXPLAINED.md](EXPLAINED.md).*

## L'idée en une minute

Chacun garde **son propre carnet** d'événements signés (techniquement un journal d'événements). Il n'y a pas de registre commun : on se montre des carnets, et chacun les vérifie par lui-même.

Il y a deux couches, indépendantes l'une de l'autre :

- **Le socle** : identité, carnet, transferts, contrats. Il est gratuit : pas de brûlage, et pas de réseau obligatoire.
- **L'accrual** : la création de nouveaux AIWA. C'est la seule chose qui demande de brûler du SOL.

| | Brûlage ? | Internet ? |
|---|---|---|
| Identité (ta clé), ton carnet | non | non |
| Époques de minage (l'horloge d'Aiwa) | non | non |
| Recevoir et transférer des AIWA | non | seulement pour faire passer les événements d'une personne à l'autre, par n'importe quel moyen (fichier, QR, texte…) ; le receveur vérifie une fois, auprès de Solana, le brûlage d'origine d'une pièce |
| Contrats, publier une application | non | non |
| **Créer de nouveaux AIWA (accrual)** | **oui** | pour le brûlage lui-même (Solana) ; ensuite le minage se fait hors ligne |

## 1. Le socle

**Ta clé.**
- Tu obtiens 12 mots qui fabriquent une clé secrète. Ton identité est l'empreinte (SHA-256) de la clé publique correspondante, et c'est aussi ton adresse Solana : les mêmes mots ouvrent le même compte dans un portefeuille Solana.
- Il n'y a ni compte ni inscription : rien n'est envoyé nulle part.

**Ton carnet.**
- Tout ce que tu fais est un événement signé avec ta clé. Son numéro est l'empreinte de son contenu, et il cite ses « parents », les événements qui le précèdent.
- Chacun ne garde que ce qui le concerne.

**Le temps, sans horloge.**
- Ton appareil fait un calcul qu'on ne peut pas accélérer en le répartissant sur plusieurs machines. Un calcul vaut une « époque », avec une preuve que n'importe qui vérifie en quelques millisecondes (environ 3,6 ms, contre environ 0,5 s pour la produire, d'après les mesures du papier).
- Faire avancer ce compteur ne demande aucun brûlage.

**Posséder et transférer des AIWA.**
- Une créance appartient à une clé. Pour la transférer, son propriétaire signe, et chaque preuve de transfert ne sert qu'une fois.
- Recevoir et passer des AIWA ne demande aucun brûlage.

**Les contrats.**
- Il n'y a pas de machine qui les exécute pour tout le monde. Un contrat est un jeu de règles (« état + événement → nouvel état ») que chacun rejoue à partir des événements qu'il détient. Une action est prouvée par une signature placée dans l'action elle-même, sinon n'importe qui pourrait se faire passer pour un autre.
- On peut aussi publier une application : signée, adressée par son contenu, immuable, avec ses versions, ouverte dans un cadre isolé qui n'a pas accès à ton portefeuille. Publier ne demande pas de brûler.
- Un contrat ne crée pas d'AIWA : il n'y a que l'accrual qui en crée.
- Ton état de contrat est celui que donnent les événements que tu as : il peut différer chez quelqu'un qui en a d'autres.

## 2. Comment les carnets se rencontrent

Aiwa demande une seule chose : que les événements d'un autre te parviennent, **par n'importe quel moyen**. Un fichier, un texte collé, un QR code, une Pull Request GitHub (le registre du store), un nœud d'archive, ou une connexion directe entre appareils. La connexion directe n'est qu'un confort ; cette application n'en a même pas.

Une fois que tu as reçu les événements d'un autre, tu peux signer un **Mirror** : « voici ce que j'ai vu de lui ». Ce n'est pas une fusion : les deux carnets restent intacts, simplement reliés. Tu peux aussi estimer où en est un autre, par les preuves et par un vote pondéré. C'est informatif ; dans ce vote, le poids d'un observateur est son brûlage confirmé.

## 3. Branches, conflits et double dépense

**Une branche n'est pas un conflit.** Deux événements qui ne se citent pas sont deux branches : c'est ordinaire. Elles se rejoignent dès que quelqu'un écrit un nouvel événement, qui cite toutes les têtes qu'il connaît.

**Ce que les signatures font et ne font pas.** Chaque événement contient l'empreinte de ses parents : modifier le passé casse tout ce qui suit. Mais rien n'empêche de signer deux suites différentes du même parent. C'est un calcul local avec la clé.

**La double dépense.** C'est exactement ce cas : deux transferts de la même pièce qui citent le même parent.
- Dès que les deux se retrouvent dans un même carnet, la pièce n'a qu'un seul propriétaire. L'autre transfert est refusé, et le rejet est consigné. La valeur n'est jamais dupliquée.
- Tous les lecteurs désignent maintenant le **même** gagnant : à égalité, c'est le plus petit identifiant d'événement. Avant, c'était celui reçu en premier, ce qui variait d'un lecteur à l'autre. (Un conflit qu'un checkpoint a déjà absorbé reste tel que le checkpoint l'a décidé.)
- C'est un accord, pas une équité : le gagnant n'est pas le premier dans le temps, et un tricheur peut fabriquer des variantes de son événement jusqu'à ce que celui qui l'arrange ait le plus petit identifiant.

**Ce qui ne peut pas être empêché hors ligne.** Quelqu'un qui détient sa clé peut signer deux dépenses sans que personne ne le voie. Celui dont le paiement perd a pu croire qu'il était payé, jusqu'à ce qu'il voie l'autre branche. L'empêcher demande une autorité commune ou une horloge, que le protocole n'a pas. Il reste : limiter le montant, la confiance, et la preuve après coup (deux signatures valides de la même clé sur deux dépenses de la même pièce, vérifiables par n'importe qui). Une ancre sur Solana n'aide que ceux qui peuvent se connecter avant de remettre ce qu'ils donnent ; elle ne sert à rien pour un échange qui reste entièrement hors ligne, et elle n'est pas construite.

**Le minage.** Bifurquer son propre historique de minage coûte du travail : chaque événement nomme le précédent dans sa partie signée, donc montrer une autre histoire oblige à refaire le calcul. Un « témoin » (n'importe qui qui garde un événement reçu) permet à un registre d'exiger qu'il figure dans l'historique montré ensuite.

**Les applications publiées.** Si deux versions bifurquent, la lecture est refusée avec une erreur et tu choisis laquelle lire.

## 4. L'accrual : créer de la valeur

**Pourquoi un coût.** Sans autorité centrale, une identité de plus doit coûter quelque chose, sinon n'importe qui en ferait mille pour créer de la monnaie gratuitement. Le coût est de brûler du SOL, de façon irréversible et vérifiable.

**Les étapes.**
1. **Brûler** du SOL en l'envoyant à l'adresse d'incinération de Solana.
2. **Choisir T** (entre 0 et 40 %) au moment du brûlage. T est une part du brûlage qui n'est pas comptée comme capital : elle est détruite, sauf une toute petite part fixe qui va au créateur du logiciel (voir plus bas). Ton capital qui mine = brûlé × (1 − T).
3. **Miner** : tes époques font monter ce que tu peux réclamer.
4. **Réclamer** : ce qui s'est accumulé devient une créance à toi.

**La part du créateur.** Le brûlage est fait de deux virements dans une seule transaction : presque tout vers l'incinérateur, et une petite part vers une adresse fixe, celle du créateur. Au départ, cette part est de 0,1 % de la part T : à T = 40 %, 0,0004 SOL par SOL brûlé ; à T = 0 (le défaut), rien. Tu ne choisis pas qui est payé, le portefeuille montre la répartition avant que tu signes, et les lecteurs rejettent un engagement à T > 0 dont la part du créateur n'a pas été payée. L'adresse et le taux sont des paramètres du protocole : ils ne changent que par une nouvelle version des règles, jamais par un réglage.

**Vérification.** Chaque lecteur va lire lui-même la transaction finalisée sur Solana (réussie, vers l'incinérateur, payée par ta clé). Sans brûlage confirmé, une réclamation est rejetée.

**La formule, en mots.** Le gain monte avec le capital et avec le temps écoulé depuis ta dernière action. Il baisse à mesure que l'identité vieillit. Un nouveau brûlage remplace ta position, et l'ancienne est d'abord payée. Des époques minées avant de brûler ne rapportent rien et ralentissent même ton rythme ensuite.

## 5. Retrouver son historique

Les 12 mots redonnent la clé, pas le carnet. Il revient par une sauvegarde signée par toi, un nœud d'archive, l'état que garde le registre du store, ou des pairs qui ont reçu tes événements. À la restauration, la sauvegarde la plus avancée est reprise, jamais un retour en arrière.

## 6. Ce qui est nouveau, et ce qui ne l'est pas

Les briques sont connues : un journal signé relié par empreintes (comme git ou Secure Scuttlebutt), une preuve de travail séquentiel (Wesolowski), le brûlage comme coût, les bons verrouillés par hachage, les clés de session. Aucune recherche d'antériorité n'a été faite. Ce qui est inhabituel, c'est la combinaison et sa conception économique :

- **Créer de la valeur sans consensus.** Chaque identité crée ses AIWA localement, à partir d'un travail séquentiel vérifiable, avec un brûlage externe comme seul portail. Dans la plupart des systèmes, l'émission est liée au consensus ; ici non, ce qui la rend compatible avec les partitions et l'hors ligne.
- **Le travail comme horloge propre à chaque identité.** Les actions sont liées au travail par la signature, donc cacher ou réordonner une action coûte du calcul. La règle « une action remplace la position et l'ancienne est payée d'abord » et la part T détruite vont avec.
- **Une preuve que n'importe quel tiers vérifie en millisecondes.** Un registre lit l'état de minage de quelqu'un sans lui faire confiance.

Pas démontré : aucun déploiement réel, pas d'essai sur le vrai devnet de Solana, des paramètres économiques non validés sur le terrain.

## 7. Ce qui n'est pas résolu

- Rien ne prouve que deux identités sont deux personnes.
- Personne n'est payé pour conserver les données des autres.
- Il n'y a pas de rendez-vous automatique entre inconnus.
- Solana est la seule dépendance externe, pour le brûlage.

**Pas vérifié :** le parcours complet n'a été joué que contre un faux Solana (25 vérifications sur 25), pas sur le vrai devnet ni sur de vrais téléphones. Que Claude écrive un contrat qui fonctionne n'a pas non plus été vérifié.

## 8. Le Store

Le Store liste des apps qui s'ouvrent dedans. Une app est un fichier HTML que son auteur a **signé** ; le Store vérifie la signature et l'empreinte avant de l'ouvrir, donc un hébergeur ne peut pas changer le fichier, puis l'ouvre dans un cadre qui n'a aucun accès à ton portefeuille ni au reste de la page.

La liste est classée selon ce que chaque auteur a **miné** : `score / laps` (ce que l'auteur peut réclamer, rapporté aux époques depuis sa dernière action), figé quand le registre a accepté la soumission. Aucune intervention éditoriale. Pour publier, un auteur doit avoir brûlé et miné : c'est le prix d'entrée, et le registre le vérifie lui-même auprès de Solana, part du créateur comprise. Le registre est un ensemble de fichiers du dépôt et un workflow qui lit une pull request comme des données, sans jamais l'exécuter.

Pas résolu : rien n'est relu ; une app peut utiliser le réseau (elle ne peut pas atteindre le portefeuille) ; le classement favorise le capital et le temps, pas la qualité ; deux auteurs peuvent n'être qu'une personne.

## Pour aller plus loin

[Yellow paper](YELLOWPAPER.md) (protocole formel, en anglais) · [`packages/core`](../packages/core) (le protocole) · [`packages/platform`](../packages/platform) (transport, stockage, nœud d'archive) · [`packages/lib`](../packages/lib) (API du portefeuille et SDK de contrats) · [plan](PLAN.md).
