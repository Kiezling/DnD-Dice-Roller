package com.kieslingdev.simpledice

import java.security.SecureRandom
import kotlin.random.asKotlinRandom

/** Device-seeded cryptographic randomness, reused without manual seeds or network access.
 * Bounded nextInt uses rejection sampling rather than a biased modulo operation.
 */
internal val diceRandom = SecureRandom().asKotlinRandom()
