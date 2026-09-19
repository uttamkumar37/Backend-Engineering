import java.util.HashMap;
import java.util.Map;

// Pattern: sliding window. Recognize by "contiguous subarray/substring" + a size or condition
// constraint. One of the highest-frequency patterns in real interview loops.
public class SlidingWindowDemo {

    static int longestSubstringWithoutRepeats(String s) {
        Map<Character, Integer> lastSeenAt = new HashMap<>();
        int windowStart = 0, best = 0;
        for (int windowEnd = 0; windowEnd < s.length(); windowEnd++) {
            char c = s.charAt(windowEnd);
            if (lastSeenAt.containsKey(c) && lastSeenAt.get(c) >= windowStart) {
                windowStart = lastSeenAt.get(c) + 1; // shrink the window past the repeat
            }
            lastSeenAt.put(c, windowEnd);
            best = Math.max(best, windowEnd - windowStart + 1);
        }
        return best;
    }

    static int maxSumFixedWindow(int[] nums, int k) {
        int windowSum = 0;
        for (int i = 0; i < k; i++) windowSum += nums[i];
        int best = windowSum;
        for (int i = k; i < nums.length; i++) {
            windowSum += nums[i] - nums[i - k]; // slide: add new, drop old - O(1) per step
            best = Math.max(best, windowSum);
        }
        return best;
    }

    public static void main(String[] args) {
        String s = "abcabcbb";
        System.out.println("Longest substring without repeats in \"" + s + "\": "
                + longestSubstringWithoutRepeats(s) + " (expected 3, \"abc\")");

        int[] nums = {2, 1, 5, 1, 3, 2};
        System.out.println("Max sum of window size 3: " + maxSumFixedWindow(nums, 3) + " (expected 9)");
    }
}
