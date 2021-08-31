package com.googleresearch.capturesync.softwaresync.phasealign;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Helper functions to works with {@link java.util.stream.Stream}.
 */
public class StreamUtils {

    private StreamUtils() {
    }

    /**
     * Constructs a new stream with the results of applying the provided function to the contents
     * of the provided streams one by one.
     * Original implementation: https://stackoverflow.com/a/23529010.
     *
     * @param a      the first stream.
     * @param b      the second stream.
     * @param zipper the function to be applied to the elements of the given streams with the same
     *               indices.
     * @return a stream containing the results of the given function.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static <A, B, C> Stream<C> zip(Stream<A> a, Stream<B> b, BiFunction<A, B, C> zipper) {
        Objects.requireNonNull(zipper);
        Spliterator<A> aSpliterator = Objects.requireNonNull(a).spliterator();
        Spliterator<B> bSpliterator = Objects.requireNonNull(b).spliterator();

        // Zipping looses DISTINCT and SORTED characteristics
        int characteristics = aSpliterator.characteristics() & bSpliterator.characteristics() &
                ~(Spliterator.DISTINCT | Spliterator.SORTED);

        // Check if both streams are SIZED and get the minimum size if possible
        long zipSize = ((characteristics & Spliterator.SIZED) != 0)
                ? Math.min(aSpliterator.getExactSizeIfKnown(), bSpliterator.getExactSizeIfKnown())
                : -1;

        Iterator<A> aIterator = Spliterators.iterator(aSpliterator);
        Iterator<B> bIterator = Spliterators.iterator(bSpliterator);
        Iterator<C> cIterator = new Iterator<C>() {
            @Override
            public boolean hasNext() {
                return aIterator.hasNext() && bIterator.hasNext();
            }

            @Override
            public C next() {
                return zipper.apply(aIterator.next(), bIterator.next());
            }
        };

        Spliterator<C> split = Spliterators.spliterator(cIterator, zipSize, characteristics);
        return StreamSupport.stream(split, a.isParallel() || b.isParallel());
    }
}
