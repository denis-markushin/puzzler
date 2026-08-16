package io.github.denismarkushin.puzzler.tracker

/**
 * Labels the tracker uses to store state.
 * Colons are avoided: Jira is fussy about special characters in labels.
 */
object PuzzleLabels {
    private const val REPO_PREFIX = "puzzler-repo-"
    private const val HASH_PREFIX = "puzzler-hash-"

    fun repo(name: String) = "$REPO_PREFIX$name"

    fun hash(hash: String) = "$HASH_PREFIX$hash"

    fun hashOf(labels: List<String>) = labels.firstOrNull { label -> label.startsWith(HASH_PREFIX) }?.removePrefix(HASH_PREFIX)
}
