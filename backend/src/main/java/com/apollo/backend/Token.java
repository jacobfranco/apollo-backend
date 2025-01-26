package com.apollo.backend;

import java.util.*;
import com.rpl.rama.*;

public class Token implements RamaSerializable {
    private static final HashSet<Character> linkBoundaryChars = new HashSet<>(Arrays.asList(
            // whitespace chars
            ' ', '\t', '\n', '\r', '\f', '\b',
            // special chars not allowed in URLs
            '`', '\'', '"', '(', ')', '[', ']', '{', '}', '<', '>'));
    private static final HashSet<Character> boundaryChars = new HashSet<>(Arrays.asList(
            // whitespace chars
            ' ', '\t', '\n', '\r', '\f', '\b',
            // special chars
            '!', '$', '%', '^', '&', '*', '?', '\\', '.', ',', '`', '\'', '"', ';',
            '|', '-', '+', '=', '(', ')', '[', ']', '{', '}', '<', '>'));

    public enum TokenKind {
        BOUNDARY,
        WORD,
        LINK,
        HASHTAG,
        MENTION,
        SPACE
    }

    public TokenKind kind;
    public String content;

    private static boolean isLink(String content) {
        return content.startsWith("http://") || content.startsWith("https://");
    }

    private static void finishToken(List<Token> tokens, Token token) {
        if (token.content.length() > 0)
            tokens.add(token);
    }

    public static List<Token> parseTokens(String content) {
        List<Token> tokens = new ArrayList<>();
        Token currentToken = new Token(TokenKind.BOUNDARY, "");

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            boolean linkParsing = currentToken.kind == TokenKind.LINK;
            Set<Character> chars = linkParsing ? linkBoundaryChars : boundaryChars;

            // All special tokens now follow the same pattern
            if (!linkParsing && ch == 's' && i + 1 < content.length() && content.charAt(i + 1) == '/') {
                finishToken(tokens, currentToken);
                String potentialSpaceId = content.substring(i + 2).split("[\\s.,!?]")[0];
                currentToken = validSpaceIds.contains(potentialSpaceId) ? new Token(TokenKind.SPACE, "")
                        : new Token(TokenKind.WORD, "s/" + potentialSpaceId);
                i++;
            }
            // Hashtags - simplified like spaces
            else if (!linkParsing && ch == '#') {
                finishToken(tokens, currentToken);
                currentToken = new Token(TokenKind.HASHTAG, "");
            }
            // Mentions - simplified like spaces
            else if (!linkParsing && ch == '@') {
                finishToken(tokens, currentToken);
                currentToken = new Token(TokenKind.MENTION, "");
            }
            // Rest of the token handling...
            else if (chars.contains(ch)) {
                if (currentToken.kind == TokenKind.BOUNDARY)
                    currentToken.content += ch;
                else {
                    finishToken(tokens, currentToken);
                    currentToken = new Token(TokenKind.BOUNDARY, String.valueOf(ch));
                }
            } else {
                if (currentToken.kind == TokenKind.BOUNDARY) {
                    finishToken(tokens, currentToken);
                    currentToken = new Token(TokenKind.WORD, String.valueOf(ch));
                } else
                    currentToken.content += ch;
                if (currentToken.kind == TokenKind.WORD && isLink(currentToken.content))
                    currentToken.kind = TokenKind.LINK;
            }
        }
        finishToken(tokens, currentToken);
        return tokens;
    }

    public static Set<String> filterHashtags(List<Token> tokens) {
        Set<String> hashtags = new HashSet<>();
        for (Token token : tokens) {
            if (token.kind == TokenKind.HASHTAG)
                hashtags.add(token.content);
        }
        return hashtags;
    }

    public static Set<String> filterMentions(List<Token> tokens) {
        HashSet<String> mentions = new HashSet<>();
        for (Token token : tokens) {
            if (token.kind == TokenKind.MENTION)
                mentions.add(token.content);
        }
        return mentions;
    }

    public static Set<String> filterLinks(List<Token> tokens) {
        HashSet<String> urls = new HashSet<>();
        for (Token token : tokens) {
            if (token.kind == TokenKind.LINK)
                urls.add(token.content);
        }
        return urls;
    }

    public static Set<String> filterSpaces(List<Token> tokens) {
        HashSet<String> spaces = new HashSet<>();
        for (Token token : tokens) {
            if (token.kind == TokenKind.SPACE)
                spaces.add(token.content);
        }
        return spaces;
    }

    public Token(TokenKind kind, String content) {
        this.kind = kind;
        this.content = content;
    }

    public String getOrigContent() {
        if (kind == TokenKind.HASHTAG)
            return "#" + content;
        else if (kind == TokenKind.MENTION)
            return "@" + content;
        else if (kind == TokenKind.SPACE)
            return "s/" + content;
        else
            return content;
    }

    @Override
    public String toString() {
        return "Token{kind=" + kind + ", content='" + content + '\'' + '}';
    }

    private static final HashSet<String> validSpaceIds = new HashSet<>(Arrays.asList(
            "7todie", "8ball", "hatintime", "plaguetale", "awayout", "aceattorney", "aq", "aoe", "aow", "alanwake",
            "alienisolation", "avp", "americantruck", "amongus", "angrybirds", "animalcrossing", "anno1800",
            "antichamber",
            "apex", "arksurvival", "arma", "armello", "armoredcore", "ac", "astrobot", "astroneer", "atelierryza",
            "atomfall", "avowed", "b4b", "balatro", "bg3", "banana", "batmanak", "battleblock", "bf", "battletech",
            "beamng", "beatsaber", "besiege", "b2s", "biomutant", "bioshock", "blackdesert", "blackmesa", "wukong",
            "blasphemous", "blockblast", "bb", "btd", "borderlands", "brawlhalla", "brotato", "bully", "cod",
            "candycrush",
            "carmechanic", "castlecrashers", "castlevania", "celeste", "checkers", "chess", "morta", "chivalry2",
            "cinnabunny", "skylines", "citizensleeper", "civ", "coc", "clashroyale", "coh", "control", "crabgame",
            "crashbandicoot", "ck", "necrodancer", "crysis", "cuphead", "cyberpunk", "ds1", "ds2", "ds3",
            "darkestdungeon",
            "darksiders", "darkwood", "dayz", "dbd", "deathstranding", "deathloop", "deeprockgalactic", "deltaforce",
            "demonssouls", "descenders", "destiny2", "becomehuman", "deus-ex", "dmc", "diablo", "discoelysium",
            "dishonored", "divinity2", "dontstarve", "doom", "dota2", "dragonage", "dbfighterz", "sparkingzero",
            "dragonquest", "df", "dragonsdogma", "dungeondefenders", "dyinglight", "dynastywarrior", "dysonsphere",
            "eafc", "eco", "efootball", "eldenring", "nightreign", "tesonline", "elex", "elitedangerous", "enderal",
            "endlessspace", "gungeon", "eternalreturn", "eutruck", "eu4", "eve", "fable", "factorio", "fallguys",
            "fallout", "farcry", "farmsim", "ff", "fireemblem", "fnaf", "fbmanager", "forhonor", "forager", "fortnite",
            "forza", "frostpunk", "ftl", "gangbeasts", "gardenscapes", "gmod", "genshin", "geoguessr", "geometrydash",
            "overit", "tsushima", "goy", "ghostwiretokyo", "goatsim", "gow", "ggd", "gta", "graveyardkeeper",
            "grimdawn",
            "gris", "guildwars", "guiltygear", "guitarhero", "gunfirereborn", "h1z1", "hades", "halflife", "halo",
            "hearthstone", "hoi", "heavyrain", "hellblade", "helldivers", "herosiege", "homm", "hots", "herosland",
            "hitman", "hogwarts", "hk", "homescapes", "hok", "hfw", "hzd", "hotlinemiami", "houseflipper", "hff",
            "humankind", "huntshowdown", "imperator", "injustice", "inscryption", "backrooms", "intergalactic", "inzoi",
            "itt", "judas", "katamari", "kenshi", "ksp", "killingfloor", "kh", "kac", "kirby", "kocity", "lanoire",
            "lol", "l4d", "botw", "totk", "legosw", "lethalcompany", "lis", "likeadragon", "limbus", "ln2", "lbp",
            "lobotomy", "loophero", "lostark", "lumaisland", "madden", "mafia", "mancala", "marathon", "mario",
            "marvelrivals", "masseffect", "maxpayne", "meddynasty", "megaman", "mg", "metalslug", "refantazio",
            "metroexodus", "metroidprime", "msflightsim", "mc", "minesweeper", "mir4", "mirrorsedge", "theshow",
            "monopoly", "monsterhunter", "monsterstrike", "monstertrain", "mordhau", "morrowind", "mk", "mnb",
            "mudrunner", "summercar", "portia", "naraka", "nba2k", "neva", "newworld", "eanhl", "replicant", "automata",
            "nioh", "nirvananoir", "nivalis", "nms", "noita", "oblivion", "okami", "oncehuman", "ori", "outerwilds",
            "outerworlds", "overcooked", "overwatch", "palworld", "poe", "payday", "persona", "pillars", "plagueinc",
            "planetside", "pvz", "pokemon", "portal", "prey", "prisonarchitect", "zomboid", "pubg", "quake", "rm3d",
            "raft", "r6", "rotmg", "rdr", "re", "rimworld", "riskofrain", "roblox", "rockband", "rl", "roguelegacy",
            "rct", "royalmatch", "runescape", "russianfishing", "rust", "saintsrow", "satisfactory", "scum",
            "seaofthieves", "sekiro", "som", "sotc", "sow", "silenthill", "skyrim", "sts", "sleepingdogs",
            "slimerancher",
            "smite", "sniperelite", "solitaire", "sonicdash", "sonic", "sotf", "spacewar", "spiderman", "splitfiction",
            "splitgate", "spore", "squad", "stalcraft", "stalker", "starcitizen", "swbattlefront", "starcraft",
            "stardew", "stellarblade", "stellaris", "stray", "sf", "strinova", "stumbleguys", "subnautica",
            "subwaysurfers", "sunhaven", "mariorun", "smash", "supervive", "taleofimmortal", "talesofarise", "tots",
            "tf2", "teardown", "tekken", "terraria", "tft", "tboi", "escapists", "evilwithin", "thefinals", "tfd",
            "theforest", "tlou", "longdark", "lotrmoria", "outlasttrials", "sims", "stanley", "talosprinciple",
            "witcher", "witcher2", "witcher3", "witness", "thehunter", "warofmine", "tal", "timberborn", "titanfall",
            "torchlight", "totalwar", "township", "tunic", "uncharted", "unchartedwaters", "undertale", "goosegame",
            "valheim", "val", "vampiresurvivors", "warrobots", "warthunder", "warframe", "wh40k", "wasteland",
            "edithfinch", "wordle", "wot", "wow", "xcom", "yugiohmd", "lolesports", "valesports", "csesports",
            "honesports", "pubgmobileesports", "pubgesports", "fortniteesports", "r6esports", "mlbbesports",
            "codesports", "apexesports", "rocketleagueesports", "tftesports", "cfesports", "freefiresports",
            "sfesports", "eafcesports", "warzoneesports", "owesports", "aovesports", "pokemonesports", "sc2esports",
            "haloesports", "tekkenesports", "mtgesports", "nba2kesports", "cocesports", "maddenesports",
            "rennsportesports", "wowesports", "idvesports", "smashesports", "hearthstoneesports", "hotsesports",
            "smiteesports", "brawlstarsesports", "clashroyaleesports"));
}