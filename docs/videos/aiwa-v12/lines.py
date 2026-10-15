import json
LINES = [
 ("Système d'élevage d'agents personnels avec github et claude code.", "Système d'élevage d'agents personnels, avec Guitheub et Claude Code."),
 ("Documents, vidéos, jeux, site internet, applications android", "Documents, vidéos, jeux, site internet, applications Androïde."),
 ("Directement dans votre propre repo.", "Directement dans votre propre répo."),
 ("Vous laissez simplement AIWA s'en occuper.", "Vous laissez simplement Aïoua s'en occuper."),
 ("Tout à la voix même depuis l'écran de verrouillage.", "Tout à la voix, même depuis l'écran de verrouillage."),
 ("Seulement 9 mots clés personnalisables.", "Seulement neuf mots clés personnalisables."),
 ("Cast TV automatique de vos créations vidéos.", "Caste télé automatique de vos créations vidéos."),
 ("Création de conversation inter session sur claude code pour des agents puissants en boucle avec mode production, critique, soutien critique.", "Création de conversations entre sessions sur Claude Code, pour des agents puissants, en boucle, avec mode production, critique, soutien critique."),
 ("Pour créer des graphs pondérés de pensée constructive.", "Pour créer des graphes pondérés de pensée constructive."),
 ("Pilote un super agent qui prend le contrôle de sessions multiples et créé avec toi.", "Pilote un super agent, qui prend le contrôle de sessions multiples, et créé avec toi."),
 ("Créations à la chaîne de haute qualité et système de campagne par simples liens de partage.", "Créations à la chaîne, de haute qualité, et système de campagne par simples liens de partage."),
 ("Adapté pour smart glasses", "Adapté pour smarte glasses."),
 ("Création d'un réseau social de découverte et de créativité de proximité.", "Création d'un réseau social de découverte et de créativité de proximité."),
 ("Installation et mise à jour automatique du cerveau via F-Droid Termux sur Android.", "Installation et mise à jour automatique du cerveau, via Ef-droïde Termuks, sur Androïde."),
 ("Mise à jour du corps par vote collectif.", "Mise à jour du corps, par vote collectif."),
 ("Grâce au token AIWA.", "Grâce au tokène Aïoua."),
 ("Le token à la base du registre interplanétaire, un store sans permission et un nouveau genre de smart contract.", "Le tokène, à la base du registre interplanétaire, un store sans permission, et un nouveau genre de smarte contracte."),
 ("Et qui brûle du Solana.", "Et qui brûle du Solana."),
 ("Pour créer un système monétaire réellement gouverné par le peuple.", "Pour créer un système monétaire, réellement gouverné par le peuple."),
 ("Grâce au Proof of Network.", "Grâce au Prouf of Netwerk."),
 ("Tu veux le code 100% Open en 2027 ?", "Tu veux le code, cent pour cent opène, en deux mille vingt-sept ?"),
 ("Partage cette vidéo.", "Partage cette vidéo."),
]
if __name__=='__main__':
    cues=[]; at=0.2
    for i,(_,v) in enumerate(LINES):
        cues.append({"at":at,"text":v}); at+=20
    json.dump(cues,open('cues_measure.json','w'),ensure_ascii=False)
