package com.apollo.backendapi;

import com.apollo.backend.*;
import com.apollo.backend.data.*;
import com.apollo.backendapi.pojos.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.stream.*;

import javax.imageio.ImageIO;

import java.util.concurrent.CompletableFuture;
import java.util.AbstractMap.SimpleEntry;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import org.bouncycastle.util.encoders.Hex;
import org.jcodec.api.*;
import org.jcodec.common.io.NIOUtils;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.crypto.password.*;
import org.springframework.web.server.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.util.UriComponentsBuilder;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

public class ApolloApiHelpers {

    private static final DelegatingPasswordEncoder PASSWORD_ENCODER;

    static {
        HashMap<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        PASSWORD_ENCODER = new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    public static String encodePassword(String password) {
        return PASSWORD_ENCODER.encode(password);
    }

    public static boolean matchesPassword(String password, String passwordHash) {
        return PASSWORD_ENCODER.matches(password, passwordHash);
    }

    public static String randomString(int size) throws NoSuchAlgorithmException {
        byte[] bytes = new byte[size];
        SecureRandom.getInstanceStrong().nextBytes(bytes);
        return Hex.toHexString(bytes);
    }

    private static S3AsyncClient S3_CLIENT = null;

    public static void initS3Client() {
        try {
            S3_CLIENT = S3AsyncClient.builder()
                    .credentialsProvider(ApolloApiManager.getAwsCredentialsProvider())
                    .region(ApolloApiManager.getAwsRegion())
                    .build();
            System.out.println("S3 client initialized successfully");
        } catch (Exception e) {
            System.err.println("Error initializing S3 client: " + e.getMessage());
            e.printStackTrace();
            throw e; // Re-throw to be caught in ApolloApiApplication
        }
    }

    public static CompletableFuture<PutObjectResponse> uploadToS3(String bucketName, String key, File file) {
        PutObjectRequest objectRequest = PutObjectRequest.builder().bucket(bucketName).key(key).build();
        return S3_CLIENT.putObject(objectRequest, AsyncRequestBody.fromFile(file));
    }

    public static boolean isValidURL(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }

    public static String createFilterContext(FilterContext context) {
        switch (context) {
            case Home:
                return "home";
            case Notifications:
                return "notifications";
            case Public:
                return "public";
            case Thread:
                return "thread";
            case Account:
                return "account";
        }
        throw new RuntimeException("Invalid filter context");
    }

    public static String createFilterAction(FilterAction action) {
        switch (action) {
            case Warn:
                return "warn";
            case Hide:
                return "hide";
        }
        throw new RuntimeException("Invalid filter action");
    }

    public static StatusVisibility createStatusVisibility(String visibilityStr) {
        if ("public".equals(visibilityStr))
            return StatusVisibility.Public;
        else if ("unlisted".equals(visibilityStr))
            return StatusVisibility.Unlisted;
        else if ("private".equals(visibilityStr))
            return StatusVisibility.Private;
        else if ("direct".equals(visibilityStr))
            return StatusVisibility.Direct;
        else
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    public static String createStatusVisibility(StatusVisibility visibility) {
        switch (visibility) {
            case Public:
                return "public";
            case Unlisted:
                return "unlisted";
            case Private:
                return "private";
            case Direct:
                return "direct";
        }
        throw new RuntimeException("Invalid visibility");
    }

    public static <T, O> void setLinkHeader(ServerWebExchange exchange,
            ApolloApiManager.QueryResults<T, O> queryResults) {
        if (queryResults.linkHeaderParams != null && !queryResults.reachedEnd) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(ApolloConfig.API_URL);
            builder.path(exchange.getRequest().getPath().pathWithinApplication().value());
            // collect the existing query params
            for (Map.Entry<String, List<String>> entry : exchange.getRequest().getQueryParams().entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
            // collect the new params (these will override the existing ones)
            for (SimpleEntry<String, String> entry : queryResults.linkHeaderParams) {
                builder.replaceQueryParam(entry.getKey(), entry.getValue());
            }
            // set the header
            exchange.getResponse().getHeaders().add("Link", String.format("<%s>; rel=\"next\"", builder.toUriString()));
        }
    }

    public static List<GetAccount> createGetAccounts(List<AccountWithId> results) {
        List<GetAccount> getAccounts = new ArrayList<>();
        for (AccountWithId result : results)
            getAccounts.add(new GetAccount(result));
        return getAccounts;
    }

    public static List<GetConversation> createGetConversations(List<Conversation> convos) {
        List<GetConversation> getConversations = new ArrayList<>();
        for (Conversation convo : convos)
            getConversations.add(new GetConversation(convo));
        return getConversations;
    }

    public static GetTag createGetTag(String hashtag, ItemStats stats, boolean isFollowing) {
        GetTag tag = new GetTag(hashtag);
        Map<Long, DayBucket> buckets = stats.dayBuckets;
        buckets.forEach((Long day, DayBucket b) -> {
            tag.history.add(new GetTag.HistoryItem(day, b.uses, b.accounts));
        });
        tag.following = isFollowing;
        return tag;
    }

    public static List<GetTag> createGetTags(Map<String, ItemStats> hashtagToStats) {
        List<GetTag> getTags = new ArrayList<>();
        hashtagToStats.forEach((String hashtag, ItemStats stats) -> {
            getTags.add(createGetTag(hashtag, stats, false));
        });
        return getTags;
    }

    public static List<GetTag> createGetTags(List<SimpleEntry<String, ItemStats>> hashtagToStats) {
        List<GetTag> getTags = new ArrayList<>();
        for (SimpleEntry<String, ItemStats> entry : hashtagToStats) {
            getTags.add(createGetTag(entry.getKey(), entry.getValue(), false));
        }
        return getTags;
    }

    public static List<GetStatus> createGetStatuses(StatusQueryResults statusQueryResults) {
        List<GetStatus> getStatuses = new ArrayList<>();
        for (StatusResultWithId result : statusQueryResults.results)
            getStatuses.add(new GetStatus(result, statusQueryResults.mentions));
        return getStatuses;
    }

    public static List<GetStatus> createGetStatuses(List<StatusQueryResult> statusQueryResults) {
        List<GetStatus> getStatuses = new ArrayList<>();
        for (StatusQueryResult statusQueryResult : statusQueryResults)
            getStatuses.add(new GetStatus(statusQueryResult.result, statusQueryResult.mentions));
        return getStatuses;
    }

    public static String getStatusResultContentText(StatusResultContent content) {
        if (content.isSetNormal())
            return content.getNormal().text;
        else if (content.isSetReply())
            return content.getReply().text;
        else if (content.isSetBoost())
            return getStatusResultContentText(content.getBoost().status.content);
        return "";
    }

    public static void setStatusLinkHeader(ServerWebExchange exchange, StatusQueryResults statusQueryResults) {
        if (statusQueryResults.isSetLastStatusPointer() && !statusQueryResults.reachedEnd) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(ApolloConfig.API_URL);
            builder.path(exchange.getRequest().getPath().pathWithinApplication().value());
            // collect the existing query params
            for (Map.Entry<String, List<String>> entry : exchange.getRequest().getQueryParams().entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
            // collect the new params (these will override the existing ones)
            builder.replaceQueryParam("max_id",
                    ApolloHelpers.serializeStatusPointer(statusQueryResults.lastStatusPointer));
            // set the header
            exchange.getResponse().getHeaders().add("Link", String.format("<%s>; rel=\"next\"", builder.toUriString()));
        }
    }

    public static Map<String, String> createSearchParams(Long nextId, String term) {
        if (nextId != null && term != null) {
            return new HashMap() {
                {
                    put("nextId", nextId);
                    put("term", term);
                }
            };
        } else {
            return null;
        }
    }

    public static Map<String, String> createSearchParams(Map searchParams) {
        Long nextId = (Long) searchParams.get("nextId");
        String term = (String) searchParams.get("term");
        return createSearchParams(nextId, term);
    }

    public static List<SimpleEntry<String, String>> createLinkHeaderParams(Map searchParams) {
        if (searchParams != null) {
            List<SimpleEntry<String, String>> params = new ArrayList<>();
            params.add(new SimpleEntry<>("start_next_id", searchParams.get("nextId") + ""));
            params.add(new SimpleEntry<>("start_term", searchParams.get("term") + ""));
            return params;
        } else {
            return null;
        }
    }

    public static String sanitize(String input, int maxLength) {
        String sanitized = input.trim();
        if (sanitized.length() > maxLength)
            return sanitized.substring(0, maxLength);
        return sanitized;
    }

    public static String sanitizeField(String input) {
        return sanitize(input, ApolloApiConfig.MAX_FIELD_LENGTH);
    }

    public static Map<String, GetMarker> createGetMarkers(Map<String, Marker> markers) {
        Map<String, GetMarker> getMarkers = new HashMap<>();
        for (Map.Entry<String, Marker> entry : markers.entrySet())
            getMarkers.put(entry.getKey(), new GetMarker(entry.getValue()));
        return getMarkers;
    }

    public static List<GetNotification> createGetNotifications(List<GetNotification.Bundle> bundles) {
        List<GetNotification> getNotifications = new ArrayList<>();
        for (GetNotification.Bundle bundle : bundles) {
            if (bundle != null)
                getNotifications.add(new GetNotification(bundle));
        }
        return getNotifications;
    }

    public static boolean isValidFile(String kind, File file) {
        try {
            if ("image".equals(kind))
                ImageIO.read(file); // make sure it's a valid image file
            else if ("video".equals(kind))
                FrameGrab.createFrameGrab(NIOUtils.readableChannel(file)); // make sure it's a valid video file
            return true;
        } catch (IOException | JCodecException e) {
            return false;
        }
    }

    public static AttachmentKind createAttachmentKind(String kindStr) {
        if ("image".equals(kindStr))
            return AttachmentKind.Image;
        else if ("video".equals(kindStr))
            return AttachmentKind.Video;
        else
            throw new RuntimeException("Invalid attachment type");
    }

    public static GetSpace createGetSpace(String spaceId, String spaceName, ItemStats stats, boolean isFollowing) {
        GetSpace space = new GetSpace(spaceId, spaceName);
        Map<Long, DayBucket> buckets = stats.dayBuckets;
        buckets.forEach((Long day, DayBucket b) -> {
            space.history.add(new GetSpace.HistoryItem(day, b.uses, b.accounts));
        });
        space.following = isFollowing;
        return space;
    }

    public static List<GetSpace> createGetSpaces(Map<String, ItemStats> spaceToStats) {
        List<GetSpace> getSpaces = new ArrayList<>();
        spaceToStats.forEach((String space, ItemStats stats) -> {
            getSpaces.add(createGetSpace(space, ApolloApiHelpers.getSpaceNameFromId(space), stats, false));
        });
        return getSpaces;
    }

    public static List<GetSpace> createGetSpaces(List<SimpleEntry<String, ItemStats>> spaceToStats) {
        List<GetSpace> getSpaces = new ArrayList<>();
        for (SimpleEntry<String, ItemStats> entry : spaceToStats) {
            getSpaces.add(createGetSpace(entry.getKey(), ApolloApiHelpers.getSpaceNameFromId(entry.getKey()),
                    entry.getValue(), false));
        }
        return getSpaces;
    }

    protected static final List<Space> SPACES = Arrays.asList(
            new Space("7todie", "7 Days to Die"),
            new Space("8ball", "8 Ball Pool"),
            new Space("hatintime", "A Hat in Time"),
            new Space("plaguetale", "A Plague Tale: Innocence"),
            new Space("awayout", "A Way Out"),
            new Space("aceattorney", "Ace Attorney"),
            new Space("aq", "Adventure Quest"),
            new Space("aoe", "Age of Empires"),
            new Space("aow", "Age of Wonders"),
            new Space("alanwake", "Alan Wake"),
            new Space("alienisolation", "Alien: Isolation"),
            new Space("avp", "Aliens vs. Predator"),
            new Space("americantruck", "American Truck Simulator"),
            new Space("amongus", "Among Us"),
            new Space("angrybirds", "Angry Birds"),
            new Space("animalcrossing", "Animal Crossing"),
            new Space("anno1800", "Anno 1800"),
            new Space("antichamber", "Antichamber"),
            new Space("apex", "Apex Legends"),
            new Space("arksurvival", "Ark: Survival Evolved"),
            new Space("arma", "Arma"),
            new Space("armello", "Armello"),
            new Space("armoredcore", "Armored Core"),
            new Space("ac", "Assassin's Creed"),
            new Space("astrobot", "Astro Bot"),
            new Space("astroneer", "Astroneer"),
            new Space("atelierryza", "Atelier Ryza"),
            new Space("atomfall", "Atomfall"),
            new Space("avowed", "Avowed"),
            new Space("b4b", "Back 4 Blood"),
            new Space("balatro", "Balatro"),
            new Space("bg3", "Baldur's Gate 3"),
            new Space("banana", "Banana"),
            new Space("batmanak", "Batman: Arkham Knight"),
            new Space("battleblock", "Battleblock Theater"),
            new Space("bf", "Battlefield"),
            new Space("battletech", "Battletech"),
            new Space("beamng", "BeamNG.drive"),
            new Space("beatsaber", "Beat Saber"),
            new Space("besiege", "Besiege"),
            new Space("b2s", "Beyond: Two Souls"),
            new Space("biomutant", "Biomutant"),
            new Space("bioshock", "Bioshock"),
            new Space("blackdesert", "Black Desert Online"),
            new Space("blackmesa", "Black Mesa"),
            new Space("wukong", "Black Myth: Wukong"),
            new Space("blasphemous", "Blasphemous 2"),
            new Space("blockblast", "Block Blast"),
            new Space("bb", "Bloodborne"),
            new Space("btd", "Bloons Tower Defense"),
            new Space("borderlands", "Borderlands"),
            new Space("brawlhalla", "Brawlhalla"),
            new Space("brotato", "Brotato"),
            new Space("bully", "Bully"),
            new Space("cod", "Call of Duty"),
            new Space("candycrush", "Candy Crush"),
            new Space("carmechanic", "Car Mechanic Simulator"),
            new Space("castlecrashers", "Castle Crashers"),
            new Space("castlevania", "Castlevania"),
            new Space("celeste", "Celeste"),
            new Space("checkers", "Checkers"),
            new Space("chess", "Chess"),
            new Space("morta", "Children of Morta"),
            new Space("chivalry2", "Chivalry 2"),
            new Space("cinnabunny", "Cinnabunny"),
            new Space("skylines", "Cities Skylines"),
            new Space("citizensleeper", "Citizen Sleeper"),
            new Space("civ", "Civilization"),
            new Space("coc", "Clash of Clans"),
            new Space("clashroyale", "Clash Royale"),
            new Space("coh", "Company of Heroes"),
            new Space("control", "Control"),
            new Space("crabgame", "Crab Game"),
            new Space("crashbandicoot", "Crash Bandicoot"),
            new Space("ck", "Crusader Kings"),
            new Space("necrodancer", "Crypt of the NecroDancer"),
            new Space("crysis", "Crysis"),
            new Space("cuphead", "Cuphead"),
            new Space("cyberpunk", "Cyberpunk 2077"),
            new Space("ds1", "Dark Souls 1"),
            new Space("ds2", "Dark Souls 2"),
            new Space("ds3", "Dark Souls 3"),
            new Space("darkestdungeon", "Darkest Dungeon"),
            new Space("darksiders", "Darksiders"),
            new Space("darkwood", "Darkwood"),
            new Space("dayz", "DayZ"),
            new Space("dbd", "Dead by Daylight"),
            new Space("deathstranding", "Death Stranding"),
            new Space("deathloop", "Deathloop"),
            new Space("deeprockgalactic", "Deep Rock Galactic"),
            new Space("deltaforce", "Delta Force"),
            new Space("demonssouls", "Demons Souls"),
            new Space("descenders", "Descenders"),
            new Space("destiny2", "Destiny 2"),
            new Space("becomehuman", "Detroit: Become Human"),
            new Space("deus-ex", "Deus Ex"),
            new Space("dmc", "Devil May Cry"),
            new Space("diablo", "Diablo"),
            new Space("discoelysium", "Disco Elysium"),
            new Space("dishonored", "Dishonored 2"),
            new Space("divinity2", "Divinity: Original Sin 2"),
            new Space("dontstarve", "Don't Starve Together"),
            new Space("doom", "Doom Eternal"),
            new Space("dota2", "Dota 2"),
            new Space("dragonage", "Dragon Age"),
            new Space("dbfighterz", "Dragon Ball FighterZ"),
            new Space("sparkingzero", "Dragon Ball Sparking Zero"),
            new Space("dragonquest", "Dragon Quest"),
            new Space("df", "DragonFable"),
            new Space("dragonsdogma", "Dragon's Dogma"),
            new Space("dungeondefenders", "Dungeon Defenders"),
            new Space("dyinglight", "Dying Light"),
            new Space("dynastywarrior", "Dynasty Warrior: Origins"),
            new Space("dysonsphere", "Dyson Sphere Program"),
            new Space("eafc", "EA FC"),
            new Space("eco", "Eco"),
            new Space("efootball", "eFootball"),
            new Space("eldenring", "Elden Ring"),
            new Space("nightreign", "Elden Ring: Nightreign"),
            new Space("tesonline", "Elder Scrolls Online"),
            new Space("elex", "Elex"),
            new Space("elitedangerous", "Elite Dangerous"),
            new Space("enderal", "Enderal: Forgotten Stories"),
            new Space("endlessspace", "Endless Space"),
            new Space("gungeon", "Enter the Gungeon"),
            new Space("eternalreturn", "Eternal Return"),
            new Space("eutruck", "Euro Truck Simulator"),
            new Space("eu4", "Europa Universalis IV"),
            new Space("eve", "EVE Online"),
            new Space("fable", "Fable"),
            new Space("factorio", "Factorio"),
            new Space("fallguys", "Fall Guys"),
            new Space("fallout", "Fallout"),
            new Space("farcry", "Far Cry"),
            new Space("farmsim", "Farming Simulator"),
            new Space("ff", "Final Fantasy"),
            new Space("fireemblem", "Fire Emblem"),
            new Space("fnaf", "Five Nights at Freddy's"),
            new Space("fbmanager", "Football Manager"),
            new Space("forhonor", "For Honor"),
            new Space("forager", "Forager"),
            new Space("fortnite", "Fortnite"),
            new Space("forza", "Forza"),
            new Space("frostpunk", "Frostpunk"),
            new Space("ftl", "FTL: Faster Than Light"),
            new Space("gangbeasts", "Gang Beasts"),
            new Space("gardenscapes", "Gardenscapes: New Acres"),
            new Space("gmod", "Garry's Mod"),
            new Space("genshin", "Genshin Impact"),
            new Space("geoguessr", "Geoguessr"),
            new Space("geometrydash", "Geometry Dash"),
            new Space("overit", "Getting Over It"),
            new Space("tsushima", "Ghost of Tsushima"),
            new Space("goy", "Ghost of Yotei"),
            new Space("ghostwiretokyo", "Ghostwire: Tokyo"),
            new Space("goatsim", "Goat Simulator"),
            new Space("gow", "God of War"),
            new Space("ggd", "Goose Goose Duck"),
            new Space("gta", "Grand Theft Auto"),
            new Space("graveyardkeeper", "Graveyard Keeper"),
            new Space("grimdawn", "Grim Dawn"),
            new Space("gris", "Gris"),
            new Space("guildwars", "Guild Wars"),
            new Space("guiltygear", "Guilty Gear"),
            new Space("guitarhero", "Guitar Hero"),
            new Space("gunfirereborn", "Gunfire Reborn"),
            new Space("h1z1", "H1Z1"),
            new Space("hades", "Hades"),
            new Space("halflife", "Half-Life"),
            new Space("halo", "Halo"),
            new Space("hearthstone", "Hearthstone"),
            new Space("hoi", "Hearts of Iron"),
            new Space("heavyrain", "Heavy Rain"),
            new Space("hellblade", "Hellblade: Senua's Sacrifice"),
            new Space("helldivers", "Helldivers 2"),
            new Space("herosiege", "Hero Siege"),
            new Space("homm", "Heroes of Might and Magic"),
            new Space("hots", "Heroes of the Storm"),
            new Space("herosland", "Hero's Land"),
            new Space("hitman", "Hitman"),
            new Space("hogwarts", "Hogwarts Legacy"),
            new Space("hk", "Hollow Knight"),
            new Space("homescapes", "Homescapes"),
            new Space("hok", "Honor of Kings"),
            new Space("hfw", "Horizon Forbidden West"),
            new Space("hzd", "Horizon Zero Dawn"),
            new Space("hotlinemiami", "Hotline Miami"),
            new Space("houseflipper", "House Flipper"),
            new Space("hff", "Human: Fall Flat"),
            new Space("humankind", "Humankind"),
            new Space("huntshowdown", "Hunt: Showdown 1896"),
            new Space("imperator", "Imperator: Rome"),
            new Space("injustice", "Injustice"),
            new Space("inscryption", "Inscryption"),
            new Space("backrooms", "Inside the Backrooms"),
            new Space("intergalactic", "Intergalactic"),
            new Space("inzoi", "Inzoi"),
            new Space("itt", "It Takes Two"),
            new Space("judas", "Judas"),
            new Space("katamari", "Katamari Series"),
            new Space("kenshi", "Kenshi"),
            new Space("ksp", "Kerbal Space Program"),
            new Space("killingfloor", "Killing Floor"),
            new Space("kh", "Kingdom Hearts"),
            new Space("kac", "Kingdoms and Castles"),
            new Space("kirby", "Kirby"),
            new Space("kocity", "Knockout City"),
            new Space("lanoire", "L.A. Noire"),
            new Space("lol", "League of Legends"),
            new Space("l4d", "Left 4 Dead"),
            new Space("botw", "Legend of Zelda: Breath of the Wild"),
            new Space("totk", "Legend of Zelda: Tears of the Kingdom"),
            new Space("legosw", "LEGO Star Wars"),
            new Space("lethalcompany", "Lethal Company"),
            new Space("lis", "Life is Strange"),
            new Space("likeadragon", "Like a Dragon: Infinite Wealth"),
            new Space("limbus", "Limbus Company"),
            new Space("ln2", "Little Nightmares II"),
            new Space("lbp", "LittleBigPlanet"),
            new Space("lobotomy", "Lobotomy Corporation"),
            new Space("loophero", "Loop Hero"),
            new Space("lostark", "Lost Ark"),
            new Space("lumaisland", "Luma Island"),
            new Space("madden", "Madden"),
            new Space("mafia", "Mafia"),
            new Space("mancala", "Mancala"),
            new Space("marathon", "Marathon"),
            new Space("mario", "Mario"),
            new Space("marvelrivals", "Marvel Rivals"),
            new Space("masseffect", "Mass Effect"),
            new Space("maxpayne", "Max Payne"),
            new Space("meddynasty", "Medieval Dynasty"),
            new Space("megaman", "Mega Man"),
            new Space("mg", "Metal Gear"),
            new Space("metalslug", "Metal Slug"),
            new Space("refantazio", "Metaphor: ReFantazio"),
            new Space("metroexodus", "Metro Exodus"),
            new Space("metroidprime", "Metroid Prime"),
            new Space("msflightsim", "Microsoft Flight Simulator"),
            new Space("mc", "Minecraft"),
            new Space("minesweeper", "Minesweeper"),
            new Space("mir4", "MIR4"),
            new Space("mirrorsedge", "Mirror's Edge"),
            new Space("theshow", "MLB The Show"),
            new Space("monopoly", "Monopoly Go"),
            new Space("monsterhunter", "Monster Hunter"),
            new Space("monsterstrike", "Monster Strike"),
            new Space("monstertrain", "Monster Train"),
            new Space("mordhau", "Mordhau"),
            new Space("morrowind", "Morrowind"),
            new Space("mk", "Mortal Kombat"),
            new Space("mnb", "Mount and Blade"),
            new Space("mudrunner", "Mudrunner"),
            new Space("summercar", "My Summer Car"),
            new Space("portia", "My Time at Portia"),
            new Space("naraka", "Naraka: Bladepoint"),
            new Space("nba2k", "NBA2K"),
            new Space("neva", "Neva"),
            new Space("newworld", "New World: Aeternum"),
            new Space("eanhl", "NHL"),
            new Space("replicant", "NieR Replicant"),
            new Space("automata", "NieR: Automata"),
            new Space("nioh", "Nioh"),
            new Space("nirvananoir", "Nirvana Noir"),
            new Space("nivalis", "Nivalis"),
            new Space("nms", "No Man's Sky"),
            new Space("noita", "Noita"),
            new Space("oblivion", "Oblivion"),
            new Space("okami", "Okami"),
            new Space("oncehuman", "Once Human"),
            new Space("ori", "Ori"),
            new Space("outerwilds", "Outer Wilds"),
            new Space("outerworlds", "Outer Worlds"),
            new Space("overcooked", "Overcooked"),
            new Space("overwatch", "Overwatch"),
            new Space("palworld", "Palworld"),
            new Space("poe", "Path of Exile"),
            new Space("payday", "Payday"),
            new Space("persona", "Persona"),
            new Space("pillars", "Pillars of Eternity"),
            new Space("plagueinc", "Plague Inc."),
            new Space("planetside", "Planetside 2"),
            new Space("pvz", "Plants vs. Zombies"),
            new Space("pokemon", "Pokemon"),
            new Space("portal", "Portal"),
            new Space("prey", "Prey"),
            new Space("prisonarchitect", "Prison Architect"),
            new Space("zomboid", "Project Zomboid"),
            new Space("pubg", "PUBG"),
            new Space("quake", "Quake"),
            new Space("rm3d", "Race Master 3D"),
            new Space("raft", "Raft"),
            new Space("r6", "Rainbow Six Siege"),
            new Space("rotmg", "Realm of the Mad God"),
            new Space("rdr", "Red Dead Redemption"),
            new Space("re", "Resident Evil"),
            new Space("rimworld", "Rimworld"),
            new Space("riskofrain", "Risk of Rain"),
            new Space("roblox", "Roblox"),
            new Space("rockband", "Rock Band"),
            new Space("rl", "Rocket League"),
            new Space("roguelegacy", "Rogue Legacy"),
            new Space("rct", "RollerCoaster Tycoon"),
            new Space("royalmatch", "Royal Match"),
            new Space("runescape", "Runescape"),
            new Space("russianfishing", "Russian Fishing 4"),
            new Space("rust", "Rust"),
            new Space("saintsrow", "Saint's Row"),
            new Space("satisfactory", "Satisfactory"),
            new Space("scum", "Scum"),
            new Space("seaofthieves", "Sea of Thieves"),
            new Space("sekiro", "Sekiro"),
            new Space("som", "Shadow of Mordor"),
            new Space("sotc", "Shadow of the Colossus"),
            new Space("sow", "Shadow of War"),
            new Space("silenthill", "Silent Hill 2"),
            new Space("skyrim", "Skyrim"),
            new Space("sts", "Slay the Spire"),
            new Space("sleepingdogs", "Sleeping Dogs"),
            new Space("slimerancher", "Slime Rancher"),
            new Space("smite", "Smite"),
            new Space("sniperelite", "Sniper Elite"),
            new Space("solitaire", "Solitaire"),
            new Space("sonicdash", "Sonic Dash"),
            new Space("sonic", "Sonic the Hedgehog"),
            new Space("sotf", "Sons of the Forest"),
            new Space("spacewar", "Spacewar"),
            new Space("spiderman", "Spider-Man"),
            new Space("splitfiction", "Split Fiction"),
            new Space("splitgate", "Splitgate"),
            new Space("spore", "Spore"),
            new Space("squad", "Squad"),
            new Space("stalcraft", "Stalcraft: X"),
            new Space("stalker", "Stalker 2"),
            new Space("starcitizen", "Star Citizen"),
            new Space("swbattlefront", "Star Wars Battlefront"),
            new Space("starcraft", "StarCraft"),
            new Space("stardew", "Stardew Valley"),
            new Space("stellarblade", "Stellar Blade"),
            new Space("stellaris", "Stellaris"),
            new Space("stray", "Stray"),
            new Space("sf", "Street Fighter"),
            new Space("strinova", "Strinova"),
            new Space("stumbleguys", "Stumble Guys"),
            new Space("subnautica", "Subnautica"),
            new Space("subwaysurfers", "Subway Surfers"),
            new Space("sunhaven", "Sun Haven"),
            new Space("mariorun", "Super Mario Run"),
            new Space("smash", "Super Smash Bros"),
            new Space("supervive", "Supervive"),
            new Space("taleofimmortal", "Tale of Immortal"),
            new Space("talesofarise", "Tales of Arise"),
            new Space("tots", "Tales of the Shire"),
            new Space("tf2", "Team Fortress 2"),
            new Space("teardown", "Teardown"),
            new Space("tekken", "Tekken"),
            new Space("terraria", "Terraria"),
            new Space("tft", "TFT"),
            new Space("tboi", "The Binding of Isaac"),
            new Space("escapists", "The Escapists"),
            new Space("evilwithin", "The Evil Within"),
            new Space("thefinals", "The Finals"),
            new Space("tfd", "The First Descendant"),
            new Space("theforest", "The Forest"),
            new Space("tlou", "The Last of Us"),
            new Space("longdark", "The Long Dark"),
            new Space("lotrmoria", "The Lord of the Rings: Return to Moria"),
            new Space("outlasttrials", "The Outlast Trials"),
            new Space("sims", "The Sims"),
            new Space("stanley", "The Stanley Parable"),
            new Space("talosprinciple", "The Talos Principle"),
            new Space("witcher", "The Witcher"),
            new Space("witcher2", "The Witcher II"),
            new Space("witcher3", "The Witcher III"),
            new Space("witness", "The Witness"),
            new Space("thehunter", "theHunter: Call of the Wild"),
            new Space("warofmine", "This War of Mine"),
            new Space("tal", "Throne and Liberty"),
            new Space("timberborn", "Timberborn"),
            new Space("titanfall", "Titanfall 2"),
            new Space("torchlight", "Torchlight"),
            new Space("totalwar", "Total War"),
            new Space("township", "Township"),
            new Space("tunic", "Tunic"),
            new Space("uncharted", "Uncharted"),
            new Space("unchartedwaters", "Uncharted Waters Origin"),
            new Space("undertale", "Undertale"),
            new Space("goosegame", "Untitled Goose Game"),
            new Space("valheim", "Valheim"),
            new Space("val", "Valorant"),
            new Space("vampiresurvivors", "Vampire Survivors"),
            new Space("warrobots", "War Robots"),
            new Space("warthunder", "War Thunder"),
            new Space("warframe", "Warframe"),
            new Space("wh40k", "Warhammer 40K: Space Marine"),
            new Space("wasteland", "Wasteland"),
            new Space("edithfinch", "What Remains of Edith Finch"),
            new Space("wordle", "Wordle"),
            new Space("wot", "World of Tanks"),
            new Space("wow", "World of Warcraft"),
            new Space("xcom", "XCOM 2"),
            new Space("yugiohmd", "Yu-Gi-Oh! Master Duel"),
            new Space("lolesports", "League of Legends Esports"),
            new Space("valesports", "Valorant Esports"),
            new Space("csesports", "Counter Strike Esports"),
            new Space("hokesports", "Honor of Kings Esports"),
            new Space("pubgmobileesports", "PUBG Mobile Esports"),
            new Space("pubgesports", "PUBG Esports"),
            new Space("fortniteesports", "Fortnite Esports"),
            new Space("r6esports", "Rainbow Six Siege Esports"),
            new Space("mlbbesports", "Mobile Legends Esports"),
            new Space("codesports", "Call of Duty Esports"),
            new Space("apexesports", "Apex Legends Esports"),
            new Space("rlesports", "Rocket League Esports"),
            new Space("tftesports", "Teamfight Tactics Esports"),
            new Space("cfesports", "CrossFire Esports"),
            new Space("freefiresports", "Free Fire Esports"),
            new Space("sfesports", "Street Fighter Esports"),
            new Space("eafcesports", "EA Sports FC Esports"),
            new Space("warzoneesports", "Call of Duty: Warzone Esports"),
            new Space("owesports", "Overwatch Esports"),
            new Space("aovesports", "Arena of Valor Esports"),
            new Space("pokemonesports", "Pokemon Esports"),
            new Space("sc2esports", "StarCraft II Esports"),
            new Space("haloesports", "Halo Esports"),
            new Space("tekkenesports", "Tekken Esports"),
            new Space("mtgesports", "Magic: The Gathering Esports"),
            new Space("nba2kesports", "NBA 2K Esports"),
            new Space("cocesports", "Clash of Clans Esports"),
            new Space("maddenesports", "Madden NFL Esports"),
            new Space("rennsportesports", "Rennsport Esports"),
            new Space("wowesports", "World of Warcraft Esports"),
            new Space("idvesports", "Identity V Esports"),
            new Space("smashesports", "Super Smash Bros Esports"),
            new Space("hearthstoneesports", "Hearthstone Esports"),
            new Space("hotsesports", "Heroes of the Storm Esports"),
            new Space("smiteesports", "Smite Esports"),
            new Space("brawlstarsesports", "Brawl Stars Esports"),
            new Space("clashroyaleesports", "Clash Royale Esports"));

    public static String getSpaceNameFromId(String id) {
        // Remove any leading '/' if present
        String normalizedId = id.startsWith("/") ? id.substring(1) : id;
        // Remove the 's/' prefix if present
        String spaceId = normalizedId.startsWith("s/") ? normalizedId.substring(2) : normalizedId;

        return SPACES.stream()
                .filter(space -> space.id.equals(spaceId))
                .findFirst()
                .map(space -> space.name)
                .orElse(spaceId);
    }

    public static boolean spaceExists(String id) {
        // same normalization logic as getSpaceNameFromId
        String normalizedId = id.startsWith("/") ? id.substring(1) : id;
        String spaceId = normalizedId.startsWith("s/") ? normalizedId.substring(2) : normalizedId;

        // Returns true if we find a matching ID in SPACES
        return SPACES.stream()
                .anyMatch(space -> space.id.equals(spaceId));
    }

}