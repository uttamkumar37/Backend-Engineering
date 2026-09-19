import java.util.Arrays;

// Pattern: dynamic programming. The highest-leverage DP skill is defining the state and
// transition BEFORE writing any code - most DP failures come from starting to code before
// nailing down what dp[i] actually represents.
public class DynamicProgrammingDemo {

    // State definition: dp[i][j] = minimum number of single-character edits (insert, delete,
    // substitute) needed to transform word1[0..i) into word2[0..j).
    // Transition: if the last characters match, dp[i][j] = dp[i-1][j-1] (no edit needed).
    // Otherwise, dp[i][j] = 1 + min(delete, insert, substitute) from the three neighboring states.
    static int editDistance(String word1, String word2) {
        int[][] dp = new int[word1.length() + 1][word2.length() + 1];

        for (int i = 0; i <= word1.length(); i++) dp[i][0] = i; // delete all of word1
        for (int j = 0; j <= word2.length(); j++) dp[0][j] = j; // insert all of word2

        for (int i = 1; i <= word1.length(); i++) {
            for (int j = 1; j <= word2.length(); j++) {
                if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    int delete = dp[i - 1][j];
                    int insert = dp[i][j - 1];
                    int substitute = dp[i - 1][j - 1];
                    dp[i][j] = 1 + Math.min(delete, Math.min(insert, substitute));
                }
            }
        }
        return dp[word1.length()][word2.length()];
    }

    // State definition: dp[amount] = fewest coins needed to make exactly `amount`.
    // Transition: dp[amount] = 1 + min(dp[amount - coin]) over every coin denomination that fits.
    static int coinChange(int[] coins, int amount) {
        int[] dp = new int[amount + 1];
        Arrays.fill(dp, Integer.MAX_VALUE - 1); // "unreachable" sentinel
        dp[0] = 0;

        for (int a = 1; a <= amount; a++) {
            for (int coin : coins) {
                if (coin <= a) {
                    dp[a] = Math.min(dp[a], 1 + dp[a - coin]);
                }
            }
        }
        return dp[amount] >= Integer.MAX_VALUE - 1 ? -1 : dp[amount];
    }

    public static void main(String[] args) {
        System.out.println("Edit distance 'horse' -> 'ros': " + editDistance("horse", "ros") + " (expected 3)");
        System.out.println("Coin change [1,2,5] for 11: " + coinChange(new int[]{1, 2, 5}, 11) + " (expected 3: 5+5+1)");
    }
}
