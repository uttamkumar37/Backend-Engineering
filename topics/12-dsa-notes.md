# Topic 12 — DSA Maintenance Notes

This is deliberately not a "learn DSA from scratch" module — at 6 years of experience, the value
isn't in relearning what a binary tree is, it's in staying pattern-fluent so a rusty recall
doesn't cost you a round you're otherwise qualified for. The framing here is maintenance and
interview mechanics, not fundamentals.

---

## 1. What DSA rounds are actually calibrating for at senior level

- **At senior/staff level, DSA is a filter, not the main signal** — most product companies weight
  system design (Topic 10) and experience/behavioral rounds more heavily for a senior hire, but a
  DSA round still exists (often earlier in the loop) specifically to screen out candidates who
  can't translate a problem into correct, reasonably efficient code under time pressure — it's a
  necessary-but-not-sufficient bar, not the differentiator. The practical implication: DSA prep
  time should be enough to clear the bar reliably, not maximized at the expense of system design
  and communication practice, which is where senior hiring decisions are actually made.
- **What a senior candidate is expected to do differently from a new-grad in the same round**:
  communicate the approach and its complexity *before* writing code, proactively name the
  trade-off between a couple of viable approaches (e.g., "we could do this in O(n log n) with
  sorting, or O(n) with a hash map trading space for time — I'll go with the hash map"), write
  clean, testable code without excessive hand-holding, and identify edge cases unprompted (empty
  input, duplicates, integer overflow, negative numbers) — the code quality bar is also implicitly
  higher, since "junior-looking" code (deeply nested conditionals, unclear variable names, no
  decomposition into helper functions) reads as a red flag at this level in a way it might not for
  an early-career candidate.
- **The most common senior-candidate failure mode in DSA rounds isn't lack of knowledge — it's
  jumping to code before finishing the discussion**, missing a constraint the interviewer stated,
  and having to backtrack expensively with limited time left. A second common failure mode is
  over-engineering a simple problem (adding unneeded abstraction, premature generalization) out of
  habit from real production code discipline — a coding interview rewards the most direct correct
  solution communicated clearly, not the most "enterprise" one.

---

## 2. Pattern-based recognition over memorized problems

The highest-leverage prep strategy at this stage is recognizing which **pattern** a new, unseen
problem maps to — most interview problems are variations on a small number of recurring shapes.
Memorizing specific solved problems has poor transfer to a slightly reworded variant; recognizing
"this is a sliding window problem" transfers to any problem with that shape.

- **Two pointers**: problems on a sorted array/string needing pair or triplet relationships (two
  sum on sorted input, container with most water, removing duplicates in place). Recognize by:
  "sorted or can be sorted" + "looking for a pair/relationship between elements."
- **Sliding window**: contiguous subarray/substring problems with a size or condition constraint
  (longest substring without repeating characters, minimum window substring, max sum subarray of
  size k). Recognize by: "contiguous" + "subarray/substring" + an optimization or fixed-size
  constraint — this is one of the highest-frequency patterns in real interview loops.
- **Fast/slow pointers (Floyd's cycle detection)**: linked list cycle detection, finding the
  middle of a list, detecting a duplicate in an array framed as a cycle problem. Recognize by:
  "linked list" + "cycle" or "middle element without knowing length upfront."
- **Binary search variants (not just "find a value in a sorted array")**: search in a rotated
  sorted array, find the first/last occurrence of a value, binary search on the *answer* (a
  distinct and often-missed sub-pattern — e.g., "minimize the maximum load" problems solved by
  binary searching over possible answer values and checking feasibility, not over the input array
  at all). Recognize by: "sorted, or monotonic property exists" — the monotonic-property version
  is the one senior candidates most often fail to spot because it doesn't look like a search
  problem on the surface.
- **BFS/DFS and graph traversal**: shortest path in an unweighted graph (BFS), connected
  components, cycle detection in a graph, topological sort (dependency ordering — directly
  relevant conceptually to build/deploy ordering problems from Topic 8), and tree traversals as a
  special case of DFS. Recognize by: explicit or implicit graph structure (a grid can be a graph,
  a dependency list is a graph) + "shortest," "reachable," "all paths," or "ordering."
- **Dynamic programming**: recognize by "optimal substructure" (the answer for a larger input can
  be built from answers to smaller subproblems) + "overlapping subproblems" (naive recursion
  recomputes the same subproblem repeatedly). The practical DP skill is defining the state and
  transition clearly *before* coding — most DP failures in interviews come from starting to code
  before nailing down what `dp[i]` (or `dp[i][j]`) actually represents, not from the coding itself.
  Common shapes: 1D DP (climbing stairs, house robber), 2D grid DP (unique paths, edit distance),
  knapsack-family (0/1 knapsack, subset sum, coin change).
- **Heap/priority queue**: "top-k," "kth largest/smallest," merging k sorted lists, running median.
  Recognize by: "kth" anything, or needing repeated access to a min/max under insertion.
- **Backtracking**: generating all permutations/combinations/subsets, constraint satisfaction
  (N-queens, sudoku solver). Recognize by: "all possible," "generate," combined with a
  pruning/validity condition.
- **Trie**: prefix-based string problems (autocomplete, longest common prefix, word search with a
  dictionary). Recognize by: repeated prefix lookups against a fixed dictionary of strings.
- **Union-Find (Disjoint Set Union)**: connected components with dynamic merging, cycle detection
  in an undirected graph, "number of provinces/islands" framed as grouping. Recognize by: "groups
  that merge over time" or "are these two elements connected," especially when queried repeatedly.

---

## 3. Complexity analysis — the part that's easy to get sloppy about with experience

- **Stating complexity precisely, including space, is expected without being asked** — "this is
  O(n)" without qualifying auxiliary space (a hash map holding up to n elements is O(n) space,
  not O(1)) is a common senior-candidate slip that reads as imprecision under scrutiny.
- **Amortized analysis matters for specific structures** — a dynamic array's `append` is O(1)
  amortized despite occasional O(n) resizes; being able to explain *why* (geometric growth means
  the total cost of all resizes up to n elements is O(n), spread over n appends) is a good signal
  of real understanding versus memorized complexity tables.
- **Recognizing when the interviewer's stated constraints imply the expected complexity class** —
  `n ≤ 20` strongly hints an exponential/backtracking solution is expected and fine; `n ≤ 10^5` or
  `10^6` rules out O(n²) and points toward O(n log n) or O(n); this reverse-engineering from
  constraints to expected approach is a real, teachable interview skill, not just intuition.

---

## 4. A maintenance cadence, not a study plan from zero

- **Spaced, pattern-rotation practice beats binge-solving a large problem count.** Since this is
  maintenance for someone who already has the fundamentals, the goal is staying fluent across all
  the patterns in Section 2, not maximizing total problems solved — solving 3 problems from 3
  different patterns in a week keeps broader coverage warm better than solving 10 problems from
  the same pattern in a row.
- **After solving a problem, the highest-value step is naming which pattern it was and why**,
  explicitly, in a short note — this is what builds the pattern-recognition transfer described in
  Section 2, and is frequently skipped in favor of just moving to the next problem once a solution
  passes.
- **Timed practice matters closer to actual interview loops** — solving without a clock builds
  understanding; solving under a 25–35 minute constraint (typical single-question interview
  slot) builds the specific skill of pacing the clarify → approach → code → test sequence, which
  is a different skill from just knowing how to solve the problem.
- **Revisiting a problem solved poorly weeks earlier is more valuable than a brand new unseen
  problem** in the same pattern — the goal is confirming the pattern recognition actually stuck,
  not novelty.

---

## Interview-depth Q&A

1. An interviewer gives you a problem and states `n ≤ 10^5`. What does this immediately rule out,
   and what complexity class should you be aiming for?
2. Explain amortized O(1) append for a dynamic array precisely enough to justify it mathematically,
   not just state it as a fact.
3. A problem says "minimize the maximum value achievable under a constraint." Why might this be a
   binary-search problem even though nothing is explicitly sorted?
4. What's the practical difference in interview signal between a candidate who immediately codes a
   brute-force solution correctly and one who states the brute force, its complexity, and then
   optimizes — even if both eventually arrive at the same optimal solution?
5. Why is defining what `dp[i]` represents before writing any code the single highest-leverage step
   in a dynamic programming problem?
6. Given your 6 years of production experience, what's a coding habit (e.g., defensive checks,
   abstraction layers) that would actually hurt you in a 30-minute DSA interview, and why?

## Proof of learning
_After each maintenance session, write one line: which pattern the problem belonged to, and
whether you recognized it within the first two minutes or had to search for it — track this over
time to see which patterns need more rotation._
