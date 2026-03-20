// ┌──────────────────────────────────────────────────────────────────────┐
// │  AUTO-GENERATED — do not edit by hand.                               │
// │  Run: ./gradlew :kap-arrow:generateValidatedOverloads                │
// └──────────────────────────────────────────────────────────────────────┘
package applicative

import arrow.core.Either
import arrow.core.NonEmptyList
import kotlinx.coroutines.async
import applicative.internal.curried

// ── zipV: parallel validation with full type inference ───────────────────

fun <E, A, B, R> zipV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
    fb: suspend () -> Either<NonEmptyList<E>, B>,
    combine: (A, B) -> R,
): Computation<Either<NonEmptyList<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val ea = da.await()
    val eb = db.await()
    if (ea is Either.Right && eb is Either.Right)
        Either.Right(combine(ea.value, eb.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, R> zipV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
    fb: suspend () -> Either<NonEmptyList<E>, B>,
    fc: suspend () -> Either<NonEmptyList<E>, C>,
    combine: (A, B, C) -> R,
): Computation<Either<NonEmptyList<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, R> zipV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
    fb: suspend () -> Either<NonEmptyList<E>, B>,
    fc: suspend () -> Either<NonEmptyList<E>, C>,
    fd: suspend () -> Either<NonEmptyList<E>, D>,
    combine: (A, B, C, D) -> R,
): Computation<Either<NonEmptyList<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, R> zipV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
    fb: suspend () -> Either<NonEmptyList<E>, B>,
    fc: suspend () -> Either<NonEmptyList<E>, C>,
    fd: suspend () -> Either<NonEmptyList<E>, D>,
    ff: suspend () -> Either<NonEmptyList<E>, F>,
    combine: (A, B, C, D, F) -> R,
): Computation<Either<NonEmptyList<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, R> zipV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
    fb: suspend () -> Either<NonEmptyList<E>, B>,
    fc: suspend () -> Either<NonEmptyList<E>, C>,
    fd: suspend () -> Either<NonEmptyList<E>, D>,
    ff: suspend () -> Either<NonEmptyList<E>, F>,
    fg: suspend () -> Either<NonEmptyList<E>, G>,
    combine: (A, B, C, D, F, G) -> R,
): Computation<Either<NonEmptyList<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, R> zipV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
    fb: suspend () -> Either<NonEmptyList<E>, B>,
    fc: suspend () -> Either<NonEmptyList<E>, C>,
    fd: suspend () -> Either<NonEmptyList<E>, D>,
    ff: suspend () -> Either<NonEmptyList<E>, F>,
    fg: suspend () -> Either<NonEmptyList<E>, G>,
    fh: suspend () -> Either<NonEmptyList<E>, H>,
    combine: (A, B, C, D, F, G, H) -> R,
): Computation<Either<NonEmptyList<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, R> zipV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
    fb: suspend () -> Either<NonEmptyList<E>, B>,
    fc: suspend () -> Either<NonEmptyList<E>, C>,
    fd: suspend () -> Either<NonEmptyList<E>, D>,
    ff: suspend () -> Either<NonEmptyList<E>, F>,
    fg: suspend () -> Either<NonEmptyList<E>, G>,
    fh: suspend () -> Either<NonEmptyList<E>, H>,
    fi: suspend () -> Either<NonEmptyList<E>, I>,
    combine: (A, B, C, D, F, G, H, I) -> R,
): Computation<Either<NonEmptyList<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, R> zipV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
    fb: suspend () -> Either<NonEmptyList<E>, B>,
    fc: suspend () -> Either<NonEmptyList<E>, C>,
    fd: suspend () -> Either<NonEmptyList<E>, D>,
    ff: suspend () -> Either<NonEmptyList<E>, F>,
    fg: suspend () -> Either<NonEmptyList<E>, G>,
    fh: suspend () -> Either<NonEmptyList<E>, H>,
    fi: suspend () -> Either<NonEmptyList<E>, I>,
    fj: suspend () -> Either<NonEmptyList<E>, J>,
    combine: (A, B, C, D, F, G, H, I, J) -> R,
): Computation<Either<NonEmptyList<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, R> zipV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
    fb: suspend () -> Either<NonEmptyList<E>, B>,
    fc: suspend () -> Either<NonEmptyList<E>, C>,
    fd: suspend () -> Either<NonEmptyList<E>, D>,
    ff: suspend () -> Either<NonEmptyList<E>, F>,
    fg: suspend () -> Either<NonEmptyList<E>, G>,
    fh: suspend () -> Either<NonEmptyList<E>, H>,
    fi: suspend () -> Either<NonEmptyList<E>, I>,
    fj: suspend () -> Either<NonEmptyList<E>, J>,
    fk: suspend () -> Either<NonEmptyList<E>, K>,
    combine: (A, B, C, D, F, G, H, I, J, K) -> R,
): Computation<Either<NonEmptyList<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, R> zipV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
    fb: suspend () -> Either<NonEmptyList<E>, B>,
    fc: suspend () -> Either<NonEmptyList<E>, C>,
    fd: suspend () -> Either<NonEmptyList<E>, D>,
    ff: suspend () -> Either<NonEmptyList<E>, F>,
    fg: suspend () -> Either<NonEmptyList<E>, G>,
    fh: suspend () -> Either<NonEmptyList<E>, H>,
    fi: suspend () -> Either<NonEmptyList<E>, I>,
    fj: suspend () -> Either<NonEmptyList<E>, J>,
    fk: suspend () -> Either<NonEmptyList<E>, K>,
    fl: suspend () -> Either<NonEmptyList<E>, L>,
    combine: (A, B, C, D, F, G, H, I, J, K, L) -> R,
): Computation<Either<NonEmptyList<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val dl = async { fl() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    val el = dl.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
            if (el is Either.Left) add(el.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, R> zipV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
    fb: suspend () -> Either<NonEmptyList<E>, B>,
    fc: suspend () -> Either<NonEmptyList<E>, C>,
    fd: suspend () -> Either<NonEmptyList<E>, D>,
    ff: suspend () -> Either<NonEmptyList<E>, F>,
    fg: suspend () -> Either<NonEmptyList<E>, G>,
    fh: suspend () -> Either<NonEmptyList<E>, H>,
    fi: suspend () -> Either<NonEmptyList<E>, I>,
    fj: suspend () -> Either<NonEmptyList<E>, J>,
    fk: suspend () -> Either<NonEmptyList<E>, K>,
    fl: suspend () -> Either<NonEmptyList<E>, L>,
    fm: suspend () -> Either<NonEmptyList<E>, M>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M) -> R,
): Computation<Either<NonEmptyList<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val dg = async { fg() }
    val dh = async { fh() }
    val di = async { fi() }
    val dj = async { fj() }
    val dk = async { fk() }
    val dl = async { fl() }
    val dm = async { fm() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    val eg = dg.await()
    val eh = dh.await()
    val ei = di.await()
    val ej = dj.await()
    val ek = dk.await()
    val el = dl.await()
    val em = dm.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
            if (eg is Either.Left) add(eg.value)
            if (eh is Either.Left) add(eh.value)
            if (ei is Either.Left) add(ei.value)
            if (ej is Either.Left) add(ej.value)
            if (ek is Either.Left) add(ek.value)
            if (el is Either.Left) add(el.value)
            if (em is Either.Left) add(em.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

// ── liftV: curried validated lift ────────────────────────────────────────

fun <E, P1, P2, R> liftV2(f: (P1, P2) -> R): Computation<Either<NonEmptyList<E>, (P1) -> (P2) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, R> liftV3(f: (P1, P2, P3) -> R): Computation<Either<NonEmptyList<E>, (P1) -> (P2) -> (P3) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, R> liftV4(f: (P1, P2, P3, P4) -> R): Computation<Either<NonEmptyList<E>, (P1) -> (P2) -> (P3) -> (P4) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, R> liftV5(f: (P1, P2, P3, P4, P5) -> R): Computation<Either<NonEmptyList<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, R> liftV6(f: (P1, P2, P3, P4, P5, P6) -> R): Computation<Either<NonEmptyList<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> R>> =
    pure(Either.Right(f.curried()))

// Additional arities (7-22) follow the same pattern and will be generated
// by ./gradlew :kap-arrow:generateValidatedOverloads
