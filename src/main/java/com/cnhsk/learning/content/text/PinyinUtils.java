package com.cnhsk.learning.content.text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PinyinUtils {

    private static final String TONED_VOWELS = "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ";
    private static final String BASE_VOWELS = "aeiouv";
    private static final int MAX_SYLLABLE_LENGTH = 7;

    // Base spellings after the spec's ü -> v conversion; tones are carried separately.
    private static final Set<String> SYLLABLES = Set.of("""
            a ai an ang ao
            ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu
            ca cai can cang cao ce cen ceng cha chai chan chang chao che chen cheng chi
            chong chou chu chua chuai chuan chuang chui chun chuo ci cong cou cu cuan cui cun cuo
            da dai dan dang dao de dei den deng di dia dian diao die ding diu dong dou du duan dui dun duo
            e ei en eng er
            fa fan fang fei fen feng fo fou fu
            ga gai gan gang gao ge gei gen geng gong gou gu gua guai guan guang gui gun guo
            ha hai han hang hao he hei hen heng hong hou hu hua huai huan huang hui hun huo
            ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun
            ka kai kan kang kao ke kei ken keng kong kou ku kua kuai kuan kuang kui kun kuo
            la lai lan lang lao le lei leng li lia lian liang liao lie lin ling liu lo long lou lu luan lun luo lv lve
            ma mai man mang mao me mei men meng mi mian miao mie min ming miu mo mou mu
            na nai nan nang nao ne nei nen neng ni nian niang niao nie nin ning niu nong nou nu nuan nuo nv nve
            o ou
            pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou pu
            qi qia qian qiang qiao qie qin qing qiong qiu qu quan que qun
            ran rang rao re ren reng ri rong rou ru ruan rui run ruo
            sa sai san sang sao se sen seng sha shai shan shang shao she shei shen sheng shi shou
            shu shua shuai shuan shuang shui shun shuo si song sou su suan sui sun suo
            ta tai tan tang tao te teng ti tian tiao tie ting tong tou tu tuan tui tun tuo
            wa wai wan wang wei wen weng wo wu
            xi xia xian xiang xiao xie xin xing xiong xiu xu xuan xue xun
            ya yan yang yao ye yi yin ying yo yong you yu yuan yue yun
            za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao zhe zhei zhen zheng zhi
            zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong zou zu zuan zui zun zuo
            """.strip().split("\\s+"));

    private PinyinUtils() {
    }

    public static String normalize(String value) {
        String normalized = normalizedInput(value);
        if (normalized == null) {
            return null;
        }

        StringBuilder withV = new StringBuilder(normalized.length());
        for (int offset = 0; offset < normalized.length(); offset++) {
            char letter = normalized.charAt(offset);
            withV.append("üǖǘǚǜ".indexOf(letter) >= 0 ? 'v' : letter);
        }

        String decomposed = Normalizer.normalize(withV, Normalizer.Form.NFD);
        StringBuilder result = new StringBuilder(decomposed.length());
        for (int offset = 0; offset < decomposed.length(); offset++) {
            char letter = decomposed.charAt(offset);
            if ((letter < '\u0300' || letter > '\u036F') && letter != ' ' && letter != '\'' && letter != '-') {
                result.append(letter);
            }
        }
        return result.toString();
    }

    public static boolean hasValidCharacters(String value) {
        String normalized = normalizedInput(value);
        if (normalized == null || normalized.isEmpty()) {
            return false;
        }

        for (int offset = 0; offset < normalized.length(); offset++) {
            char letter = normalized.charAt(offset);
            if ((letter < 'a' || letter > 'z') && letter != 'ü' && TONED_VOWELS.indexOf(letter) < 0
                    && letter != ' ' && letter != '\'' && letter != '-') {
                return false;
            }
        }
        return true;
    }

    /**
     * Converts tone-mark Pinyin to lowercase numbered syllables separated by spaces.
     * Invalid characters or unsupported syllable boundaries cause IllegalArgumentException;
     * callers that grade learner input must handle that case as invalid input.
     */
    public static String toNumbered(String value) {
        String normalized = normalizedInput(value);
        if (normalized == null || normalized.isEmpty()) {
            return normalized;
        }
        if (!hasValidCharacters(normalized)) {
            throw new IllegalArgumentException("Pinyin contains unsupported characters.");
        }

        List<String> result = new ArrayList<>();
        for (String word : normalized.split("[ '-]+")) {
            if (!word.isEmpty()) {
                result.addAll(numberSyllables(word));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Pinyin must contain syllables.");
        }
        return String.join(" ", result);
    }

    private static String normalizedInput(String value) {
        String normalized = TextNormalizer.normalizeCell("pinyin", value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static List<String> numberSyllables(String word) {
        StringBuilder letters = new StringBuilder(word.length());
        int[] tones = new int[word.length()];
        for (int index = 0; index < word.length(); index++) {
            char letter = word.charAt(index);
            int markedVowel = TONED_VOWELS.indexOf(letter);
            letters.append(markedVowel < 0 ? (letter == 'ü' ? 'v' : letter) : BASE_VOWELS.charAt(markedVowel / 4));
            tones[index] = markedVowel < 0 ? 0 : markedVowel % 4 + 1;
        }

        String plain = letters.toString();
        int length = plain.length();
        int[] ways = new int[length + 1];
        int[] next = new int[length];
        int[] selectedTone = new int[length];
        ways[length] = 1;

        // Analyze suffixes iteratively so long input does not exhaust the call stack.
        for (int start = length - 1; start >= 0; start--) {
            int tone = 5;
            boolean marked = false;
            for (int end = start + 1; end <= Math.min(length, start + MAX_SYLLABLE_LENGTH); end++) {
                if (tones[end - 1] != 0) {
                    if (marked) {
                        break;
                    }
                    tone = tones[end - 1];
                    marked = true;
                }
                // Joined a/o/e syllables need an explicit separator (e.g. xi'an).
                if ((end < length && "aoe".indexOf(plain.charAt(end)) >= 0)
                        || ways[end] == 0 || !isSyllable(plain.substring(start, end))) {
                    continue;
                }
                next[start] = end;
                selectedTone[start] = tone;
                ways[start] = Math.min(2, ways[start] + ways[end]);
            }
        }

        if (ways[0] != 1) {
            throw new IllegalArgumentException("Pinyin syllables cannot be determined uniquely; use separators.");
        }

        List<String> result = new ArrayList<>();
        for (int start = 0; start < length; start = next[start]) {
            result.add(plain.substring(start, next[start]) + selectedTone[start]);
        }
        return result;
    }

    private static boolean isSyllable(String value) {
        if (SYLLABLES.contains(value)) {
            return true;
        }
        // An erhua r stays part of the preceding syllable, rather than becoming r5.
        return value.length() > 1 && value.endsWith("r")
                && !value.substring(0, value.length() - 1).endsWith("r")
                && SYLLABLES.contains(value.substring(0, value.length() - 1));
    }
}
