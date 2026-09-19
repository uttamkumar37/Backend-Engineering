import java.util.Arrays;

// Pattern: two pointers. Recognize by "sorted (or sortable)" + "looking for a pair/relationship."
public class TwoPointersDemo {

    // Two Sum on a SORTED array: O(n) instead of the O(n^2) brute force or O(n) extra-space hash map.
    static int[] twoSumSorted(int[] sorted, int target) {
        int left = 0, right = sorted.length - 1;
        while (left < right) {
            int sum = sorted[left] + sorted[right];
            if (sum == target) return new int[]{left, right};
            if (sum < target) left++; else right--;
        }
        return new int[]{-1, -1};
    }

    // Container With Most Water: move the pointer at the SHORTER wall inward, since the
    // shorter wall is always the bottleneck - moving the taller one can only make it worse.
    static int maxWaterArea(int[] heights) {
        int left = 0, right = heights.length - 1, best = 0;
        while (left < right) {
            int width = right - left;
            int area = width * Math.min(heights[left], heights[right]);
            best = Math.max(best, area);
            if (heights[left] < heights[right]) left++; else right--;
        }
        return best;
    }

    public static void main(String[] args) {
        int[] sorted = {2, 7, 11, 15};
        int[] result = twoSumSorted(sorted, 18);
        System.out.println("Two Sum (target=18) indices: " + Arrays.toString(result)
                + " -> values " + sorted[result[0]] + " + " + sorted[result[1]]);

        int[] heights = {1, 8, 6, 2, 5, 4, 8, 3, 7};
        System.out.println("Max water area: " + maxWaterArea(heights) + " (expected 49)");
    }
}
