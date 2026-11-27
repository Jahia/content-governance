// The luxe quality dataset of content-governance: test pages under /sites/luxe/home/quality-lab, each
// of which breaks one family of rules, and the compliance terms that the page "compliance" breaks.
// README.md beside this file lists each planted error and the finding it gives.
//
// The script removes the dataset first, so a second run resets every page that a fix changed. It runs
// through the provisioning API (dataset.py, or a manifest with executeScript), or pasted into
// /modules/tools/groovyConsole.jsp. It needs the site luxe, with English and French, and its images
// published under files/site.
import org.jahia.api.Constants
import org.jahia.services.content.JCRCallback
import org.jahia.services.content.JCRPublicationService
import org.jahia.services.content.JCRTemplate

def SITE = 'luxe'
def HOME = '/sites/' + SITE + '/home'
def ROOT = HOME + '/quality-lab'
def TERMS = '/sites/' + SITE + '/contents/quality-lab-terms'
def FILES = '/sites/' + SITE + '/files/site/'
def IMG = '/files/live' + FILES
def LANGUAGES = ['en', 'fr']

// The address of a page of the lab in a language, as an editor's link stores it. Jahia turns it into a
// reference on save, so the link renders in every mode.
def link = { String lang, String page -> '/cms/render/live/' + lang + ROOT + (page ? '/' + page : '') + '.html' }
def site = { String lang, String page -> '/cms/render/live/' + lang + HOME + '/' + page + '.html' }

// ---------------------------------------------------------------------------------------------------
// The pages. Each value of a map keyed by language is written in that language only. A page with no
// fr value stays untranslated on purpose.
// ---------------------------------------------------------------------------------------------------
def pages = []

pages << [name: 'quality-lab', parent: HOME, publish: true,
    title: [en: 'Quality lab: content test pages', fr: 'Laboratoire qualité : pages de test'],
    description: [en: 'Test pages of the LUXE site. Each one breaks one kind of content rule, so that an editor can try the content governance checks.',
                  fr: 'Pages de test du site LUXE. Chacune enfreint un type de règle, pour essayer les contrôles de la gouvernance des contenus.'],
    ogImage: FILES + 'home/life_style.jpg',
    header: [title: [en: 'Quality lab', fr: 'Laboratoire qualité'],
             subtitle: [en: 'Pages that break the rules on purpose', fr: 'Des pages qui enfreignent les règles exprès'],
             image: FILES + 'home/life_style.jpg'],
    texts: [
        intro: [
            en: '<h2>What this page is for</h2>' +
                '<p>This page is the reference of the lab. It follows the rules that an editor can fix. Open it first, then open the content governance drawer.</p>' +
                '<p>Each page below breaks one family of rules on purpose. Open a page and compare the drawer with the list in the guide of the lab.</p>' +
                '<h2>The test pages</h2>' +
                '<ul>' +
                '<li><a href="' + link('en', 'accessibility') + '">Accessibility: villas on the French Riviera</a></li>' +
                '<li><a href="' + link('en', 'seo') + '">Search engines: apartments in Geneva</a></li>' +
                '<li><a href="' + link('en', 'thin-page') + '">A page with almost no text: Monaco</a></li>' +
                '<li><a href="' + link('en', 'readability') + '">Readability: the Paris market</a></li>' +
                '<li><a href="' + link('en', 'jahia-health') + '">Jahia health: a penthouse in New York</a></li>' +
                '<li><a href="' + link('en', 'geo-members-only') + '">AI crawlers: listings for members</a></li>' +
                '<li><a href="' + link('en', 'compliance') + '">Compliance: a guide for investors</a></li>' +
                '</ul>',
            fr: '<h2>À quoi sert cette page</h2>' +
                '<p>Cette page est la référence du laboratoire. Elle suit les règles qu\'un rédacteur peut corriger. Ouvrez-la d\'abord, puis ouvrez le panneau de la gouvernance des contenus.</p>' +
                '<p>Chaque page ci-dessous enfreint une famille de règles exprès. Ouvrez une page et comparez le panneau avec la liste du guide du laboratoire.</p>' +
                '<h2>Les pages de test</h2>' +
                '<ul>' +
                '<li><a href="' + link('fr', 'accessibility') + '">Accessibilité : villas sur la Côte d\'Azur</a></li>' +
                '<li><a href="' + link('fr', 'seo') + '">Moteurs de recherche : appartements à Genève</a></li>' +
                '<li><a href="' + link('fr', 'thin-page') + '">Une page presque vide : Monaco</a></li>' +
                '<li><a href="' + link('fr', 'readability') + '">Lisibilité : le marché parisien</a></li>' +
                '<li><a href="' + link('fr', 'jahia-health') + '">Santé Jahia : un penthouse à New York</a></li>' +
                '<li><a href="' + link('fr', 'geo-members-only') + '">Robots des IA : biens réservés aux membres</a></li>' +
                '<li><a href="' + link('fr', 'compliance') + '">Conformité : un guide pour investisseurs</a></li>' +
                '</ul>'],
        reset: [
            en: '<h2>How to reset the lab</h2>' +
                '<p>A fix changes the content of a test page. Run the install script again to plant the errors once more. It removes the lab and builds it again in a few seconds.</p>' +
                '<p>The script does not touch the other pages of the site. It writes only under this page and in one folder of terms.</p>',
            fr: '<h2>Remettre le laboratoire à zéro</h2>' +
                '<p>Une correction change le contenu d\'une page de test. Lancez de nouveau le script d\'installation pour replacer les erreurs. Il supprime le laboratoire et le reconstruit en quelques secondes.</p>' +
                '<p>Le script ne touche pas aux autres pages du site. Il écrit seulement sous cette page et dans un dossier de termes.</p>'],
        homes: [
            en: '<h2>About our homes</h2>' +
                '<p>We sell homes in Paris, Geneva and New York, and on the French Riviera. Our team visits each home before we list it. We check the plans, the deeds and the state of the roof.</p>' +
                '<p>We give you the price, the size and the fees in one place. A visit takes about one hour. You can book it by phone or by email, and we answer within one working day.</p>' +
                '<p><img src="' + IMG + 'home/boat.jpg" alt="A wooden motor boat at anchor in a calm bay at sunset"></p>' +
                '<p>Do you want to sell? Read <a href="' + site('en', 'sell') + '">how we sell a home</a>. Do you want to meet us? Find <a href="' + site('en', 'agencies') + '">the agency closest to you</a>.</p>' +
                '<h2>What we promise</h2>' +
                '<p>We tell you what we know about a home, and what we do not know yet. We never share your name with a seller or a buyer. We keep your files for the time the law asks, and not one day more.</p>' +
                '<p>We work with notaries, surveyors and lawyers in each city. They answer your questions in your language. You pay their fees only when you sign.</p>',
            fr: '<h2>Nos biens</h2>' +
                '<p>Nous vendons des biens à Paris, Genève et New York, et sur la Côte d\'Azur. Notre équipe visite chaque bien avant de le proposer. Nous vérifions les plans, les titres et l\'état de la toiture.</p>' +
                '<p>Nous donnons le prix, la surface et les frais au même endroit. Une visite dure environ une heure. Vous la réservez par téléphone ou par e-mail, et nous répondons sous un jour ouvré.</p>' +
                '<p><img src="' + IMG + 'home/boat.jpg" alt="Un bateau à moteur en bois au mouillage dans une baie calme au coucher du soleil"></p>' +
                '<p>Vous voulez vendre ? Lisez <a href="' + site('fr', 'sell') + '">comment nous vendons un bien</a>. Vous voulez nous rencontrer ? Trouvez <a href="' + site('fr', 'agencies') + '">l\'agence la plus proche</a>.</p>' +
                '<h2>Ce que nous promettons</h2>' +
                '<p>Nous vous disons ce que nous savons d\'un bien, et ce que nous ne savons pas encore. Nous ne donnons jamais votre nom à un vendeur ou à un acheteur. Nous gardons vos dossiers le temps que la loi demande, et pas un jour de plus.</p>' +
                '<p>Nous travaillons avec des notaires, des géomètres et des avocats dans chaque ville. Ils répondent à vos questions dans votre langue. Vous payez leurs frais seulement à la signature.</p>']]]

pages << [name: 'accessibility', parent: ROOT, publish: true,
    title: [en: 'Villas on the French Riviera', fr: 'Villas sur la Côte d\'Azur'],
    description: [en: 'Five villas between Cannes and Menton, with sea views, gardens and pools. Each one is open for private viewings this season.',
                  fr: 'Cinq villas entre Cannes et Menton, avec vue sur la mer, jardin et piscine. Chacune se visite en privé cette saison.'],
    ogImage: FILES + 'home/cover_home.jpg',
    header: [title: [en: 'Villas on the French Riviera', fr: 'Villas sur la Côte d\'Azur'],
             subtitle: [en: 'Accessibility test page', fr: 'Page de test de l\'accessibilité'],
             image: FILES + 'home/cover_home.jpg'],
    texts: [
        collection: [
            en: '<h1>Our Riviera collection</h1>' +
                '<p>Five villas, from Cannes to Menton. Each one has a garden, a pool and a view of the sea. The smallest has four bedrooms. The largest has seven, and a guest house by the gate.</p>' +
                '<p><img src="' + IMG + 'home/cover_home.jpg"></p>' +
                '<h2>Cap Ferrat</h2>' +
                '<h4>Villa Les Oliviers</h4>' +
                '<p>The villa sits on a hill above the port. Its terraces face south. Old olive trees shade the lawn, and a path leads down to a small beach.</p>' +
                '<h3></h3>' +
                '<p>To see the plans of the villa, <a href="' + site('en', 'buy') + '">click here</a>. For the list of prices, <a href="' + site('en', 'sell') + '">read more</a>.</p>' +
                '<p><a href="' + site('en', 'agencies') + '"><img src="' + IMG + 'home/life_style.jpg" alt=""></a></p>',
            fr: '<h1>Notre collection sur la Riviera</h1>' +
                '<p>Cinq villas, de Cannes à Menton. Chacune a un jardin, une piscine et une vue sur la mer. La plus petite a quatre chambres. La plus grande en a sept, et une maison d\'amis près du portail.</p>' +
                '<p><img src="' + IMG + 'home/cover_home.jpg"></p>' +
                '<h2>Cap Ferrat</h2>' +
                '<h4>Villa Les Oliviers</h4>' +
                '<p>La villa est sur une colline au-dessus du port. Ses terrasses donnent au sud. De vieux oliviers ombragent la pelouse, et un chemin descend vers une petite plage.</p>' +
                '<h3></h3>' +
                '<p>Pour voir les plans de la villa, <a href="' + site('fr', 'buy') + '">cliquez ici</a>. Pour la liste des prix, <a href="' + site('fr', 'sell') + '">lire la suite</a>.</p>' +
                '<p><a href="' + site('fr', 'agencies') + '"><img src="' + IMG + 'home/life_style.jpg" alt=""></a></p>'],
        prices: [
            en: '<h2>Prices and sizes</h2>' +
                '<table><tbody>' +
                '<tr><td>Villa</td><td>Bedrooms</td><td>Living area</td><td>Price</td></tr>' +
                '<tr><td>Les Oliviers, Cap Ferrat</td><td>5</td><td>420 m²</td><td>EUR 14,500,000</td></tr>' +
                '<tr><td>La Pinède, Cannes</td><td>4</td><td>310 m²</td><td>EUR 8,900,000</td></tr>' +
                '<tr><td>Le Belvédère, Menton</td><td>7</td><td>560 m²</td><td>EUR 19,800,000</td></tr>' +
                '</tbody></table>' +
                '<p><span style="color:#c9c9c9">Viewings by appointment only. Prices include the agency fees and exclude the notary fees.</span></p>' +
                '<p><img src="' + IMG + 'home/boat.jpg"></p>',
            fr: '<h2>Prix et surfaces</h2>' +
                '<table><tbody>' +
                '<tr><td>Villa</td><td>Chambres</td><td>Surface habitable</td><td>Prix</td></tr>' +
                '<tr><td>Les Oliviers, Cap Ferrat</td><td>5</td><td>420 m²</td><td>14 500 000 EUR</td></tr>' +
                '<tr><td>La Pinède, Cannes</td><td>4</td><td>310 m²</td><td>8 900 000 EUR</td></tr>' +
                '<tr><td>Le Belvédère, Menton</td><td>7</td><td>560 m²</td><td>19 800 000 EUR</td></tr>' +
                '</tbody></table>' +
                '<p><span style="color:#c9c9c9">Visites sur rendez-vous uniquement. Les prix comprennent les honoraires d\'agence, hors frais de notaire.</span></p>' +
                '<p><img src="' + IMG + 'home/boat.jpg"></p>'],
        contact: [
            en: '<h2>Visit the villas</h2>' +
                '<p id="riviera-contact">Our office in Nice organises the viewings. A property advisor meets you at the villa and stays with you for the whole visit.</p>' +
                '<p id="riviera-contact">We can also arrange a car from the airport, and a lunch with the architect of the villa.</p>' +
                '<p>Leave your email and we call you back: <input type="email" placeholder="Your email address"> <button type="submit"></button></p>' +
                '<p>For events on the coast, see the <a href="https://www.cotedazur-tourisme.com/" target="_blank">Riviera tourist office</a>.</p>',
            fr: '<h2>Visiter les villas</h2>' +
                '<p id="riviera-contact">Notre bureau de Nice organise les visites. Un conseiller vous attend à la villa et reste avec vous pendant toute la visite.</p>' +
                '<p id="riviera-contact">Nous pouvons aussi organiser une voiture depuis l\'aéroport, et un déjeuner avec l\'architecte de la villa.</p>' +
                '<p>Laissez votre e-mail et nous vous rappelons : <input type="email" placeholder="Votre adresse e-mail"> <button type="submit"></button></p>' +
                '<p>Pour les événements de la côte, voyez l\'<a href="https://www.cotedazur-tourisme.com/" target="_blank">office de tourisme de la Riviera</a>.</p>'],
        coast: [
            en: '<h2>Life on the coast</h2>' +
                '<p>The villas are between ten and forty minutes from Nice airport. The coast road links the villages, and a train runs along the sea every half hour. Each village has a market, a bakery and a harbour.</p>' +
                '<p>The season starts in May and ends in October. In winter the coast is quiet, and most restaurants stay open. Many owners spend the spring and the autumn here, when the light is soft and the beaches are calm.</p>' +
                '<p>We can arrange a guardian, a gardener and a pool service for each villa. We send you a short report each month, with photos of the house and the garden.</p>',
            fr: '<h2>Vivre sur la côte</h2>' +
                '<p>Les villas sont à dix à quarante minutes de l\'aéroport de Nice. La route du littoral relie les villages, et un train longe la mer toutes les demi-heures. Chaque village a un marché, une boulangerie et un port.</p>' +
                '<p>La saison commence en mai et finit en octobre. En hiver la côte est calme, et la plupart des restaurants restent ouverts. Beaucoup de propriétaires passent le printemps et l\'automne ici, quand la lumière est douce et les plages tranquilles.</p>' +
                '<p>Nous pouvons organiser un gardien, un jardinier et l\'entretien de la piscine pour chaque villa. Nous vous envoyons un court rapport chaque mois, avec des photos de la maison et du jardin.</p>']]]

pages << [name: 'seo', parent: ROOT, publish: true,
    title: [en: 'Apartments for sale in Geneva with a view of the lake and the Alps',
            fr: 'Appartements à vendre à Genève avec vue sur le lac et sur les Alpes'],
    description: [en: 'Discover our exclusive selection of luxury apartments for sale in Geneva, from the Old Town to Cologny, with views of Lake Geneva and the Alps, private parking spaces, concierge services and much more for international buyers.',
                  fr: 'Découvrez notre sélection exclusive d\'appartements de luxe à vendre à Genève, de la Vieille-Ville à Cologny, avec vue sur le Léman et les Alpes, places de parc privées, conciergerie et bien plus encore pour les acheteurs internationaux.'],
    header: [title: [en: 'Apartments in Geneva', fr: 'Appartements à Genève'],
             subtitle: [en: 'Search engine test page', fr: 'Page de test du référencement'],
             image: FILES + 'destinations/destination-Geneva'],
    texts: [
        geneva: [
            en: '<h2>Living by the lake</h2>' +
                '<p>Geneva is a small city with a large lake. Most of our apartments face the water or the Alps. Some are in the Old Town, near the cathedral. Others are in Cologny, on the quiet side of the lake.</p>' +
                '<p>Each apartment has a cellar and at least one parking space. Most buildings have a concierge. The largest flats have a terrace on the roof.</p>' +
                '<h2>Buying as a foreign resident</h2>' +
                '<p>A buyer who does not live in Switzerland needs a permit for most homes. We explain the rules and prepare the file with a notary. Read <a href="http://localhost/sites/luxe/home/buy.html">our buying guide</a> before your first visit.</p>' +
                '<p>The city publishes its own rules for buyers on <a href="http://www.ge.ch/">the website of the canton</a>. A chalet in the mountains can be a good second home: see <a href="' + link('en', 'draft-offer') + '">the chalet in Megève</a>.</p>' +
                '<h2>Prices</h2>' +
                '<p>A two-bedroom flat in the centre costs about CHF 2,400,000. A family flat in Cologny costs between CHF 4,000,000 and CHF 9,000,000. The notary fees and the transfer tax add about five per cent.</p>' +
                '<p>We publish each price with its fees. We tell you how long a flat has been for sale, and whether the seller has lowered the price. You can ask for the plans and the bills of the building at any time.</p>' +
                '<p>Our office in Geneva is open from Monday to Saturday. A property advisor who speaks your language takes you to each visit and answers your questions on the spot.</p>',
            fr: '<h2>Vivre au bord du lac</h2>' +
                '<p>Genève est une petite ville avec un grand lac. La plupart de nos appartements donnent sur l\'eau ou sur les Alpes. Certains sont dans la Vieille-Ville, près de la cathédrale. D\'autres sont à Cologny, sur la rive calme du lac.</p>' +
                '<p>Chaque appartement a une cave et au moins une place de parc. La plupart des immeubles ont un concierge. Les plus grands appartements ont une terrasse sur le toit.</p>' +
                '<h2>Acheter sans résider en Suisse</h2>' +
                '<p>Un acheteur qui ne vit pas en Suisse a besoin d\'une autorisation pour la plupart des biens. Nous expliquons les règles et préparons le dossier avec un notaire. Lisez <a href="http://localhost/fr/sites/luxe/home/buy.html">notre guide d\'achat</a> avant votre première visite.</p>' +
                '<p>Le canton publie ses propres règles sur <a href="http://www.ge.ch/">son site</a>. Un chalet à la montagne peut être une bonne résidence secondaire : voyez <a href="' + link('fr', 'draft-offer') + '">le chalet de Megève</a>.</p>' +
                '<h2>Les prix</h2>' +
                '<p>Un deux-chambres au centre coûte environ 2 400 000 CHF. Un appartement familial à Cologny coûte entre 4 000 000 et 9 000 000 CHF. Les frais de notaire et les droits de mutation ajoutent environ cinq pour cent.</p>' +
                '<p>Nous publions chaque prix avec ses frais. Nous vous disons depuis quand un bien est en vente, et si le vendeur a baissé son prix. Vous pouvez demander les plans et les charges de l\'immeuble à tout moment.</p>' +
                '<p>Notre bureau de Genève est ouvert du lundi au samedi. Un conseiller qui parle votre langue vous accompagne à chaque visite et répond à vos questions sur place.</p>'],
        services: [
            en: '<h2>After the purchase</h2>' +
                '<p>After the purchase, we help you settle in. We find a cleaner, a handyman and a company for the move. We can also open your accounts with the utilities.</p>' +
                '<p>If you rent the flat out, our partners find a tenant and manage the lease. They send you a statement each month, with the rent, the charges and the works.</p>' +
                '<p>Many of our clients keep the same property advisor for years. Call us when you want to sell, to buy a second home or simply to know the value of your flat.</p>',
            fr: '<h2>Après l\'achat</h2>' +
                '<p>Après l\'achat, nous vous aidons à vous installer. Nous trouvons une aide ménagère, un artisan et une entreprise de déménagement. Nous pouvons aussi ouvrir vos contrats d\'eau et d\'électricité.</p>' +
                '<p>Si vous louez l\'appartement, nos partenaires trouvent un locataire et gèrent le bail. Ils vous envoient un relevé chaque mois, avec le loyer, les charges et les travaux.</p>' +
                '<p>Beaucoup de nos clients gardent le même conseiller pendant des années. Appelez-nous pour vendre, pour acheter une résidence secondaire ou simplement pour connaître la valeur de votre appartement.</p>']]]

pages << [name: 'thin-page', parent: ROOT, publish: true,
    title: [en: 'Monaco', fr: 'Monaco'],
    header: [title: [en: 'Monaco', fr: 'Monaco'],
             subtitle: [en: 'Thin page test', fr: 'Test d\'une page trop courte']],
    texts: [
        soon: [
            en: '<p>Our new office in Monaco opens in the spring, near the port and the casino. We will list a few apartments with a sea view there. Leave us your details and we will send you the first listings.</p>',
            fr: '<p>Notre nouveau bureau de Monaco ouvre au printemps, près du port et du casino. Nous y proposerons quelques appartements avec vue sur la mer. Laissez-nous vos coordonnées et nous vous enverrons les premières annonces.</p>']]]

pages << [name: 'readability', parent: ROOT, publish: true,
    title: [en: 'The Paris property market in 2026', fr: 'Le marché immobilier parisien en 2026'],
    description: [en: 'Prices, demand and supply of prime residential property in Paris in 2026, by district, with the outlook of our analysts for the next two years.',
                  fr: 'Prix, demande et offre de l\'immobilier résidentiel de prestige à Paris en 2026, par arrondissement, avec les perspectives de nos analystes.'],
    ogImage: FILES + 'destinations/destination-Paris',
    header: [title: [en: 'The Paris property market in 2026', fr: 'Le marché immobilier parisien en 2026'],
             subtitle: [en: 'Readability test page', fr: 'Page de test de la lisibilité'],
             image: FILES + 'destinations/destination-Paris'],
    texts: [
        analysis: [
            en: '<h2>Market analysis</h2>' +
                '<p>Notwithstanding the macroeconomic uncertainties that characterised the preceding financial year, the prime residential segment of the Parisian metropolitan market demonstrated a considerably more resilient performance than the aforementioned forecasts of the majority of institutional analysts had anticipated, particularly within the historically constrained inventory of the seventh, eighth and sixteenth arrondissements, where the intensification of international demand, combined with the persistent scarcity of exceptional properties exhibiting uninterrupted perspectives over the Seine, generated an upward pressure on valuations that experienced practitioners consider to be structurally sustainable over the medium term.</p>' +
                '<p>The progressive normalisation of monetary policy by the European Central Bank, which necessitated a comprehensive recalibration of financing strategies among leveraged purchasers, has simultaneously contributed to the disintermediation of conventional mortgage channels in favour of alternative capitalisation structures, including family office allocations, Lombard facilities collateralised against diversified securities portfolios and intergenerational patrimonial arrangements, thereby modifying the sociodemographic composition of the purchasing population in ways that conventional quantitative methodologies are insufficiently equipped to capture with satisfactory precision.</p>' +
                '<p>Furthermore, the institutional investment community has progressively reconsidered the comparative attractiveness of residential assets relative to commercial office properties, whose occupancy fundamentals have been durably undermined by the generalisation of hybrid working arrangements, thereby redirecting substantial pools of capital towards high-specification residential developments, serviced apartment configurations and heritage conversions situated within the historic arrondissements, notwithstanding the considerable regulatory complexity associated with the transformation of buildings subject to architectural preservation constraints, the protracted administrative procedures that municipal authorities impose upon developers seeking the requisite authorisations for change of use, and the associated financial guarantees that lenders increasingly require before committing long-term financing to projects whose commercial viability depends upon the successful completion of lengthy and uncertain planning negotiations.</p>',
            fr: '<h2>Analyse du marché</h2>' +
                '<p>Nonobstant les incertitudes macroéconomiques qui ont caractérisé l\'exercice précédent, le segment résidentiel de prestige du marché métropolitain parisien a manifesté une résilience considérablement supérieure aux anticipations formulées par la majorité des analystes institutionnels, tout particulièrement au sein de l\'inventaire historiquement contraint des septième, huitième et seizième arrondissements, où l\'intensification de la demande internationale, conjuguée à la raréfaction persistante des biens d\'exception bénéficiant de perspectives dégagées sur la Seine, a engendré sur les valorisations une pression haussière que les professionnels expérimentés considèrent comme structurellement soutenable à moyen terme.</p>' +
                '<p>La normalisation progressive de la politique monétaire de la Banque centrale européenne, qui a nécessité une recalibration exhaustive des stratégies de financement des acquéreurs recourant à l\'endettement, a simultanément contribué à la désintermédiation des circuits hypothécaires conventionnels au profit de structures de capitalisation alternatives, notamment les allocations de family offices, les crédits lombards adossés à des portefeuilles de valeurs mobilières diversifiés et les montages patrimoniaux intergénérationnels, modifiant de ce fait la composition sociodémographique de la population acquéreuse selon des modalités que les méthodologies quantitatives conventionnelles appréhendent insuffisamment.</p>'],
        outlook: [
            en: '<h2>Outlook</h2>' +
                '<p>Consequently, our research department anticipates that the transactional volume observed in the prime segment will continue its gradual recovery throughout the forthcoming biennial period, contingent upon the stabilisation of long-term interest rates, the maintenance of the fiscal framework applicable to non-resident acquirers and the absence of significant geopolitical disruptions susceptible to deteriorating the confidence of the international clientele, whose representation among purchasers of properties exceeding five million euros has reached an unprecedented proportion according to the latest notarial statistics available.</p>' +
                '<p>Purchasers contemplating an acquisition should accordingly undertake a comprehensive evaluation of the total cost of ownership, encompassing notarial disbursements, registration duties, the annual property wealth taxation regime, the condominium charges associated with prestigious Haussmannian buildings and the substantial renovation expenditures frequently necessitated by the obsolescence of technical installations, before formalising any irrevocable commitment.</p>',
            fr: '<h2>Perspectives</h2>' +
                '<p>En conséquence, notre département de recherche anticipe que le volume transactionnel observé sur le segment de prestige poursuivra son redressement progressif au cours de la période biennale à venir, sous réserve de la stabilisation des taux d\'intérêt à long terme, du maintien du cadre fiscal applicable aux acquéreurs non résidents et de l\'absence de perturbations géopolitiques significatives susceptibles de détériorer la confiance de la clientèle internationale, dont la représentation parmi les acquéreurs de biens excédant cinq millions d\'euros a atteint une proportion sans précédent selon les statistiques notariales les plus récentes.</p>' +
                '<p>Les acquéreurs envisageant une acquisition devraient par conséquent procéder à une évaluation exhaustive du coût global de détention, englobant les débours notariaux, les droits d\'enregistrement, le régime d\'imposition sur la fortune immobilière, les charges de copropriété afférentes aux immeubles haussmanniens prestigieux et les dépenses de rénovation substantielles fréquemment rendues nécessaires par l\'obsolescence des installations techniques, avant de formaliser tout engagement irrévocable.</p>']]]

pages << [name: 'jahia-health', parent: ROOT, publish: true,
    title: [en: 'Penthouse on the Upper East Side'],
    description: [en: 'A penthouse of 380 m² on Fifth Avenue, with a terrace over Central Park, four bedrooms and a private elevator. Viewings on request.'],
    ogImage: FILES + 'destinations/destination-NewYork',
    header: [title: [en: 'Penthouse on the Upper East Side'],
             subtitle: [en: 'Jahia health test page'],
             image: FILES + 'destinations/destination-NewYork'],
    texts: [
        penthouse: [
            en: '<h2>The penthouse</h2>' +
                '<p>The penthouse takes the whole top floor of a building from 1928. Its terrace runs along three sides and looks over Central Park. The living room has a fireplace and a ceiling of five metres.</p>' +
                '<p>There are four bedrooms, each with its own bathroom. A private elevator opens into the hall. The building has a doorman day and night, a gym and a cellar for each flat.</p>' +
                '<h2>Price</h2>' +
                '<p>Price: TODO confirm the price with the seller before we publish.</p>' +
                '<p>Floor plan: luxe:label.floorPlan.download</p>'],
        neighbourhood: [
            en: '<h2>The neighbourhood</h2>' +
                '<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.</p>' +
                '<p>The Metropolitan Museum is a ten-minute walk away. Madison Avenue, with its shops and galleries, is one block east. Several well-known schools are close by.</p>' +
                '<p>Our office in New York arranges each viewing. A property advisor meets you in the lobby and shows you the flat, the terrace and the service areas. We send you the plans, the monthly charges and the tax bill of the flat before the visit.</p>' +
                '<p>The building is a cooperative. Its board approves each buyer, and we help you prepare the file. The process takes about six weeks.</p>'],
        building: [
            en: '<h2>The building</h2>' +
                '<p>The building has fourteen floors and twenty-two flats. It was restored in 2019, with a new roof, new windows and new elevators. The lobby keeps its marble floor and its bronze doors.</p>' +
                '<p>The monthly charges cover the doorman, the heating, the water and the upkeep of the common areas. The board meets four times a year. Its minutes of the last two years are available on request.</p>' +
                '<p>Pets are welcome. The building allows works from Monday to Friday, from nine to five, once the board has approved the plans.</p>'],
        'late-change': [
            en: '<h2>Viewings</h2>' +
                '<p>Viewings take place on weekdays, from nine to six. Ask our office in New York for a time.</p>']],
    // Changed after the publication, so the page holds an unpublished change.
    afterPublish: ['late-change': '<h2>Viewings</h2><p>Viewings take place on weekdays, from nine to six, and on Saturday mornings. Ask our office in New York for a time.</p>'],
    // Added after the publication, so it was never published.
    addAfterPublish: ['late-addition': '<h2>Open house</h2><p>An open house takes place on the first Sunday of each month, from two to five. No appointment is needed.</p>']]

pages << [name: 'geo-members-only', parent: ROOT, publish: true, guestDenied: true,
    title: [en: 'Off-market listings for members', fr: 'Biens off-market pour nos membres'],
    description: [en: 'Homes that our clients sell in confidence, without public listing. Sign in to see the details, the prices and the photos of each home.',
                  fr: 'Des biens que nos clients vendent en toute discrétion, sans annonce publique. Connectez-vous pour voir le détail, le prix et les photos.'],
    ogImage: FILES + 'destinations/destination-Mykonos',
    header: [title: [en: 'Off-market listings', fr: 'Biens off-market'],
             subtitle: [en: 'AI crawler test page', fr: 'Page de test des robots des IA'],
             image: FILES + 'destinations/destination-Mykonos'],
    texts: [
        listings: [
            en: '<h2>Selling in confidence</h2>' +
                '<p>Some owners do not want their home on a public website. They may be well known, or they may want to test the market first. We show their homes only to clients we have met.</p>' +
                '<p>This page lists those homes. Only signed-in clients can read it, so a search engine and an AI assistant cannot read it either.</p>' +
                '<h2>This season</h2>' +
                '<p>A villa on Mykonos, above the bay of Agios Lazaros, with six bedrooms and a pool cut into the rock. A townhouse in the Marais with a private garden. A chalet in Verbier, a few steps from the lifts. A loft in Tribeca with a roof terrace.</p>' +
                '<p>Each listing gives the price, the size, the plans and the fees. A property advisor calls you within a day to arrange a visit. We ask you to sign a short letter of confidence before we share the address.</p>' +
                '<h2>How to join</h2>' +
                '<p>Ask your property advisor for an account. We open it after a first meeting, in one of our offices or by video. The account is free, and you can close it at any time.</p>' +
                '<p>We never share your name with a seller before you ask us to. We keep your search and your visits private, and we delete them when you close the account.</p>',
            fr: '<h2>Vendre en toute discrétion</h2>' +
                '<p>Certains propriétaires ne veulent pas de leur bien sur un site public. Ils sont parfois connus, ou veulent d\'abord tester le marché. Nous montrons leurs biens seulement aux clients que nous avons rencontrés.</p>' +
                '<p>Cette page liste ces biens. Seuls les clients connectés peuvent la lire : un moteur de recherche et un assistant IA ne la lisent pas non plus.</p>' +
                '<h2>Cette saison</h2>' +
                '<p>Une villa à Mykonos, au-dessus de la baie d\'Agios Lazaros, avec six chambres et une piscine taillée dans la roche. Un hôtel particulier dans le Marais avec un jardin privé. Un chalet à Verbier, à quelques pas des remontées. Un loft à Tribeca avec un toit-terrasse.</p>' +
                '<p>Chaque annonce donne le prix, la surface, les plans et les frais. Un conseiller vous appelle sous un jour pour organiser une visite. Nous vous demandons de signer une courte lettre de confidentialité avant de donner l\'adresse.</p>' +
                '<h2>Devenir membre</h2>' +
                '<p>Demandez un compte à votre conseiller. Nous l\'ouvrons après un premier rendez-vous, dans une de nos agences ou en vidéo. Le compte est gratuit, et vous pouvez le fermer à tout moment.</p>' +
                '<p>Nous ne donnons jamais votre nom à un vendeur avant que vous le demandiez. Nous gardons vos recherches et vos visites privées, et nous les supprimons quand vous fermez le compte.</p>'],
        benefits: [
            en: '<h2>What members receive</h2>' +
                '<p>Members receive a short letter each month with the new homes of the season. Each home comes with its plans, its fees and the name of the property advisor in charge. You can ask for a visit by replying to the letter.</p>' +
                '<p>We also invite members to private events. Think of an evening in a new building in Geneva, or a lunch with an architect in Paris. These events are free and small, with twenty guests at most.</p>' +
                '<p>You choose the cities that interest you, and you can change them at any time from your account.</p>',
            fr: '<h2>Ce que reçoivent les membres</h2>' +
                '<p>Les membres reçoivent chaque mois une courte lettre avec les nouveaux biens de la saison. Chaque bien est présenté avec ses plans, ses frais et le nom du conseiller qui le suit. Vous demandez une visite en répondant à la lettre.</p>' +
                '<p>Nous invitons aussi les membres à des événements privés. Par exemple une soirée dans un nouvel immeuble à Genève, ou un déjeuner avec un architecte à Paris. Ces événements sont gratuits et réservés à vingt invités au plus.</p>' +
                '<p>Vous choisissez les villes qui vous intéressent, et vous pouvez les changer à tout moment depuis votre compte.</p>']]]

pages << [name: 'compliance', parent: ROOT, publish: true,
    title: [en: 'Investing in prime real estate', fr: 'Investir dans l\'immobilier de prestige'],
    description: [en: 'What a private investor should know before buying a prime home as an investment: yields, taxes, fees and the risks of each market.',
                  fr: 'Ce qu\'un investisseur privé doit savoir avant d\'acheter un bien de prestige : rendement, impôts, frais et risques de chaque marché.'],
    ogImage: FILES + 'destinations/destination-Montreal',
    header: [title: [en: 'Investing in prime real estate', fr: 'Investir dans l\'immobilier de prestige'],
             subtitle: [en: 'Compliance test page', fr: 'Page de test de la conformité'],
             image: FILES + 'destinations/destination-Montreal'],
    texts: [
        guide: [
            en: '<h2>Why prime homes</h2>' +
                '<p>A prime home in a large city keeps its value better than most assets. It is also a home that you can use, lend to your family or rent out. Our clients buy for all three reasons.</p>' +
                '<p>Our Paris portfolio offers a guaranteed return of six per cent a year. It is a risk-free investment, and the rents are tax-free for the first three years. We always get you the best price on the market.</p>' +
                '<p>To book a call with our investment desk, <a href="' + site('en', 'agencies') + '">click here</a>.</p>' +
                '<h2>What it costs</h2>' +
                '<p>Count the notary fees, the transfer tax, the agency fees and the yearly charges of the building. In Paris, the fees add about eight per cent to the price. In Geneva, they add about five per cent.</p>' +
                '<p>A rented home also pays income tax on its rents, and some cities tax empty homes. Ask your tax adviser before you sign. We can introduce you to advisers who know each market.</p>' +
                '<h2>How we help</h2>' +
                '<p>We study the market of each district. We give you the prices of the last sales and the rents of similar homes. We tell you how long a home has been for sale, and why the seller sells, when the seller allows it.</p>' +
                '<p>After the purchase, our partners can rent and manage the home for you. They send you a statement each month, with the rents, the charges and the works.</p>',
            fr: '<h2>Pourquoi les biens de prestige</h2>' +
                '<p>Un bien de prestige dans une grande ville garde sa valeur mieux que la plupart des actifs. C\'est aussi un logement que vous pouvez occuper, prêter à votre famille ou louer. Nos clients achètent pour ces trois raisons.</p>' +
                '<p>Notre portefeuille parisien offre un rendement garanti de six pour cent par an. C\'est un placement sans risque, et nous vous obtenons toujours le meilleur prix du marché.</p>' +
                '<p>Pour réserver un appel avec notre équipe d\'investissement, <a href="' + site('fr', 'agencies') + '">cliquez ici</a>.</p>' +
                '<h2>Ce que cela coûte</h2>' +
                '<p>Comptez les frais de notaire, les droits de mutation, les honoraires d\'agence et les charges annuelles de l\'immeuble. À Paris, les frais ajoutent environ huit pour cent au prix. À Genève, environ cinq pour cent.</p>' +
                '<p>Un bien loué paie aussi l\'impôt sur ses loyers, et certaines villes taxent les logements vides. Consultez votre conseiller fiscal avant de signer. Nous pouvons vous présenter des conseillers qui connaissent chaque marché.</p>' +
                '<h2>Comment nous aidons</h2>' +
                '<p>Nous étudions le marché de chaque quartier. Nous donnons les prix des dernières ventes et les loyers de biens comparables. Nous vous disons depuis quand un bien est en vente, et pourquoi le vendeur vend, quand il nous y autorise.</p>' +
                '<p>Après l\'achat, nos partenaires peuvent louer et gérer le bien pour vous. Ils vous envoient un relevé chaque mois, avec les loyers, les charges et les travaux.</p>'],
        risks: [
            en: '<h2>The risks to know</h2>' +
                '<p>Prices can fall, even in sought-after areas. A home can stay empty for months between two tenants. Works on a building can cost more than planned, and a change in the law can raise the taxes on a second home.</p>' +
                '<p>Currencies move too. A buyer who earns in dollars and buys in euros or francs takes a risk on the exchange rate. Ask your bank how to cover it.</p>' +
                '<p>We describe these risks in each investment note. We give the sources of our figures and the date of each one.</p>',
            fr: '<h2>Les risques à connaître</h2>' +
                '<p>Les prix peuvent baisser, même dans les quartiers recherchés. Un bien peut rester vide des mois entre deux locataires. Les travaux d\'un immeuble peuvent coûter plus que prévu, et une loi peut augmenter les impôts sur une résidence secondaire.</p>' +
                '<p>Les devises bougent aussi. Un acheteur qui gagne en dollars et achète en euros ou en francs prend un risque de change. Demandez à votre banque comment le couvrir.</p>' +
                '<p>Nous décrivons ces risques dans chaque note d\'investissement. Nous donnons la source de nos chiffres et la date de chacun.</p>']]]

pages << [name: 'draft-offer', parent: ROOT, publish: false,
    title: [en: 'Draft offer: a chalet in Megève', fr: 'Offre en préparation : un chalet à Megève'],
    description: [en: 'A chalet of 300 m² at the foot of the Mont d\'Arbois slopes, with five bedrooms, a spa and a garage. Not published yet: the links to it are broken.',
                  fr: 'Un chalet de 300 m² au pied des pistes du Mont d\'Arbois, avec cinq chambres, un spa et un garage. Pas encore publié : les liens vers lui sont cassés.'],
    header: [title: [en: 'A chalet in Megève', fr: 'Un chalet à Megève'],
             subtitle: [en: 'Never published: the target of a broken link', fr: 'Jamais publiée : la cible d\'un lien cassé']],
    texts: [
        chalet: [
            en: '<p>This page stays unpublished. The page "seo" links to it, so its link is broken for a visitor.</p>',
            fr: '<p>Cette page reste non publiée. La page « seo » pointe vers elle, donc son lien est cassé pour un visiteur.</p>']]]

// ---------------------------------------------------------------------------------------------------
// The compliance terms that the page "compliance" breaks. content-governance reads them in the edit
// workspace, so they stay unpublished. Each one is a content rule with a pattern, in the subdomain terms.
// ---------------------------------------------------------------------------------------------------
def terms = [
    [name: 'guaranteed-return', ruleId: 'compliance.luxe-guaranteed-return', language: 'en', kind: 'forbidden',
     pattern: 'guaranteed return*', matchMode: 'term', severity: 'critical', blocking: true,
     label: [en: 'Guaranteed return', fr: 'Rendement garanti (en)'],
     advice: [en: 'A return on real estate is never guaranteed. Give the past yield of comparable homes, with its source and date.',
              fr: 'Un rendement immobilier n\'est jamais garanti. Donnez le rendement passé de biens comparables, avec sa source et sa date.']],
    [name: 'risk-free', ruleId: 'compliance.luxe-risk-free', language: 'en', kind: 'forbidden',
     pattern: 'risk[- ]free', matchMode: 'regex', severity: 'serious', blocking: false,
     label: [en: 'Risk-free', fr: 'Sans risque (en)'],
     advice: [en: 'No investment is free of risk. Name the main risks instead.',
              fr: 'Aucun placement n\'est sans risque. Nommez plutôt les principaux risques.']],
    [name: 'tax-free', ruleId: 'compliance.luxe-tax-free', language: 'en', kind: 'replace',
     pattern: 'tax-free', replacement: 'tax-efficient', matchMode: 'term', severity: 'serious', blocking: false,
     label: [en: 'Tax-free', fr: 'Exonéré d\'impôt (en)'],
     advice: [en: 'We do not give tax advice. Write "tax-efficient" and point to a tax adviser.',
              fr: 'Nous ne donnons pas de conseil fiscal. Écrivez « tax-efficient » et renvoyez vers un conseiller fiscal.']],
    [name: 'best-price', ruleId: 'compliance.luxe-best-price', language: 'en', kind: 'forbidden',
     pattern: 'best price*', matchMode: 'term', severity: 'moderate', blocking: false,
     label: [en: 'Best price', fr: 'Meilleur prix (en)'],
     advice: [en: 'A promise of the best price cannot be proven. Describe how we set the price instead.',
              fr: 'Une promesse de meilleur prix ne se prouve pas. Décrivez plutôt comment nous fixons le prix.']],
    [name: 'rendement-garanti', ruleId: 'compliance.luxe-rendement-garanti', language: 'fr', kind: 'forbidden',
     pattern: 'rendement garanti', matchMode: 'term', severity: 'critical', blocking: true,
     label: [en: 'Guaranteed return (fr)', fr: 'Rendement garanti'],
     advice: [en: 'A return on real estate is never guaranteed. Give the past yield of comparable homes, with its source and date.',
              fr: 'Un rendement immobilier n\'est jamais garanti. Donnez le rendement passé de biens comparables, avec sa source et sa date.']],
    [name: 'sans-risque', ruleId: 'compliance.luxe-sans-risque', language: 'fr', kind: 'forbidden',
     pattern: 'sans risque', matchMode: 'term', severity: 'serious', blocking: false,
     label: [en: 'Risk-free (fr)', fr: 'Sans risque'],
     advice: [en: 'No investment is free of risk. Name the main risks instead.',
              fr: 'Aucun placement n\'est sans risque. Nommez plutôt les principaux risques.']],
    [name: 'meilleur-prix', ruleId: 'compliance.luxe-meilleur-prix', language: 'fr', kind: 'forbidden',
     pattern: 'meilleur prix', matchMode: 'term', severity: 'moderate', blocking: false,
     label: [en: 'Best price (fr)', fr: 'Meilleur prix'],
     advice: [en: 'A promise of the best price cannot be proven. Describe how we set the price instead.',
              fr: 'Une promesse de meilleur prix ne se prouve pas. Décrivez plutôt comment nous fixons le prix.']],
    [name: 'cliquez-ici', ruleId: 'compliance.luxe-cliquez-ici', language: 'fr', kind: 'replace',
     pattern: 'cliquez ici', replacement: 'un texte qui nomme la page', matchMode: 'term', severity: 'serious', blocking: false,
     label: [en: 'Click here (fr)', fr: 'Cliquez ici'],
     advice: [en: 'A link text names its target. content-governance seeds "click here" in the default language of the site only.',
              fr: 'Un texte de lien nomme sa cible. content-governance ne crée « click here » que dans la langue par défaut du site.']]]

// ---------------------------------------------------------------------------------------------------
// Remove, write in each language, publish.
// ---------------------------------------------------------------------------------------------------
def report = []

[Constants.LIVE_WORKSPACE, Constants.EDIT_WORKSPACE].each { ws ->
    JCRTemplate.getInstance().doExecuteWithSystemSession(null, ws, Locale.ENGLISH, { session ->
        [ROOT, TERMS].each { p ->
            if (session.nodeExists(p)) {
                session.getNode(p).remove()
                report << 'removed ' + p + ' in ' + ws
            }
        }
        session.save()
        return null
    } as JCRCallback)
}

def weak = { session, String path -> session.getValueFactory().createValue(session.getNode(path), true) }

LANGUAGES.each { lang ->
    JCRTemplate.getInstance().doExecuteWithSystemSession(null, Constants.EDIT_WORKSPACE, new Locale(lang), { session ->
        def first = lang == LANGUAGES[0]
        // The pages first, so that a link to any page of the lab resolves when a text is saved.
        pages.each { spec ->
            if (first) {
                def page = session.getNode(spec.parent).addNode(spec.name, 'jnt:page')
                page.setProperty('j:templateName', 'free')
                if (spec.description) {
                    page.addMixin('jmix:seoHtmlHead')
                }
                if (spec.ogImage) {
                    page.setProperty('openGraphImage', weak(session, spec.ogImage))
                }
                page.addNode('main', 'jnt:contentList')
            }
            def page = session.getNode(spec.parent + '/' + spec.name)
            if (spec.title[lang]) {
                page.setProperty('jcr:title', spec.title[lang])
            }
            if (spec.description && spec.description[lang]) {
                page.setProperty('jcr:description', spec.description[lang])
            }
        }
        session.save()
        pages.each { spec ->
            def main = session.getNode(spec.parent + '/' + spec.name + '/main')
            if (first) {
                def header = main.addNode('header', 'luxe:header')
                if (spec.header.image) {
                    header.setProperty('image', weak(session, spec.header.image))
                }
                spec.texts.each { name, values -> main.addNode(name, 'jnt:bigText') }
            }
            def header = main.getNode('header')
            if (spec.header.title[lang]) {
                header.setProperty('title', spec.header.title[lang])
                header.setProperty('subtitle', spec.header.subtitle[lang])
            }
            spec.texts.each { name, values ->
                if (values[lang]) {
                    main.getNode(name).setProperty('text', values[lang])
                }
            }
        }
        session.save()
        report << 'wrote ' + pages.size() + ' pages in ' + lang
        return null
    } as JCRCallback)
}

// The members page: the guest user may not read it, in live as in edit.
JCRTemplate.getInstance().doExecuteWithSystemSession(null, Constants.EDIT_WORKSPACE, Locale.ENGLISH, { session ->
    pages.findAll { it.guestDenied }.each { spec ->
        session.getNode(spec.parent + '/' + spec.name).denyRoles('u:guest', ['reader'] as Set)
    }
    session.save()
    return null
} as JCRCallback)

def publication = JCRPublicationService.getInstance()
pages.findAll { it.publish }.each { spec ->
    def id = JCRTemplate.getInstance().doExecuteWithSystemSession(null, Constants.EDIT_WORKSPACE, Locale.ENGLISH, { session ->
        session.getNode(spec.parent + '/' + spec.name).getIdentifier()
    } as JCRCallback)
    publication.publishByMainId(id, Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, LANGUAGES as Set, false, null)
    report << 'published ' + spec.name
}

// The changes made after the publication, and the terms, whose blocking rules would refuse the
// publication of the page "compliance".
JCRTemplate.getInstance().doExecuteWithSystemSession(null, Constants.EDIT_WORKSPACE, Locale.ENGLISH, { session ->
    pages.findAll { it.afterPublish }.each { spec ->
        spec.afterPublish.each { name, html -> session.getNode(spec.parent + '/' + spec.name + '/main/' + name).setProperty('text', html) }
    }
    pages.findAll { it.addAfterPublish }.each { spec ->
        spec.addAfterPublish.each { name, html -> session.getNode(spec.parent + '/' + spec.name + '/main').addNode(name, 'jnt:bigText').setProperty('text', html) }
    }
    def contents = session.getNode('/sites/' + SITE + '/contents')
    def folder = contents.addNode(TERMS.substring(TERMS.lastIndexOf('/') + 1), 'jnt:contentFolder')
    terms.each { term ->
        def rule = folder.addNode(term.name, 'ccs:contentRule')
        // The mixin of the check type declares the pattern and the other fields of a term.
        rule.addMixin('ccsmix:patternCheck')
        rule.setProperty('ruleId', term.ruleId)
        rule.setProperty('checkType', 'PATTERN')
        rule.setProperty('evaluator', 'pattern')
        rule.setProperty('subdomain', 'terms')
        rule.setProperty('severity', term.severity)
        rule.setProperty('blocking', term.blocking)
        rule.setProperty('kind', term.kind)
        rule.setProperty('pattern', term.pattern)
        rule.setProperty('matchMode', term.matchMode)
        rule.setProperty('ruleLanguage', term.language)
        if (term.replacement) {
            rule.setProperty('replacement', term.replacement)
        }
        rule.setProperty('owner', 'LUXE legal team')
    }
    session.save()
    return null
} as JCRCallback)
LANGUAGES.each { lang ->
    JCRTemplate.getInstance().doExecuteWithSystemSession(null, Constants.EDIT_WORKSPACE, new Locale(lang), { session ->
        def folder = session.getNode(TERMS)
        folder.setProperty('jcr:title', lang == 'fr' ? 'Termes du laboratoire qualité' : 'Quality lab terms')
        terms.each { term ->
            def rule = folder.getNode(term.name)
            rule.setProperty('label', term.label[lang])
            rule.setProperty('advice', term.advice[lang])
        }
        session.save()
        return null
    } as JCRCallback)
}
report << 'wrote ' + terms.size() + ' compliance terms in ' + TERMS

log.info('luxe quality dataset: {}', report.join('; '))
return report.join('\n')
