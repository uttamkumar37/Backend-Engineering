// Pattern: binary search on the ANSWER, not on the input array - the sub-pattern senior
// candidates most often fail to spot because it doesn't look like a search problem on the
// surface. Recognize by "minimize the maximum" / "maximize the minimum" + a monotonic
// feasibility check ("can we achieve X or better?").
public class BinarySearchOnAnswerDemo {

    // Split `nums` into `k` contiguous subarrays, minimizing the largest subarray sum.
    static int minimizeMaxSubarraySum(int[] nums, int k) {
        int low = 0, high = 0;
        for (int n : nums) { low = Math.max(low, n); high += n; }

        while (low < high) {
            int mid = low + (high - low) / 2;
            if (canSplitWithMaxSum(nums, k, mid)) {
                high = mid; // mid is feasible - try to do even better
            } else {
                low = mid + 1; // mid is too small - need a bigger cap
            }
        }
        return low;
    }

    // feasibility check: can we split into <= k pieces if no piece may exceed `maxSum`?
    // this monotonic property (bigger maxSum -> always at least as feasible) is what makes
    // binary search valid here at all.
    static boolean canSplitWithMaxSum(int[] nums, int k, int maxSum) {
        int pieces = 1, currentSum = 0;
        for (int n : nums) {
            if (currentSum + n > maxSum) {
                pieces++;
                currentSum = 0;
            }
            currentSum += n;
        }
        return pieces <= k;
    }

    public static void main(String[] args) {
        int[] nums = {7, 2, 5, 10, 8};
        int k = 2;
        System.out.println("Split " + java.util.Arrays.toString(nums) + " into " + k + " pieces,");
        System.out.println("minimizing the largest piece's sum: " + minimizeMaxSubarraySum(nums, k)
                + " (expected 18 - split as [7,2,5] and [10,8])");
    }
}
