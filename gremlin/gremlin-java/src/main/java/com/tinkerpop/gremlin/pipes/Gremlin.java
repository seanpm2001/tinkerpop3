package com.tinkerpop.gremlin.pipes;

import com.tinkerpop.gremlin.pipes.util.Holder;
import com.tinkerpop.gremlin.pipes.util.HolderIterator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Gremlin<S, E> implements GremlinPipeline<S, E> {

    private final List<Pipe> pipes = new ArrayList<>();
    private Map<String, Integer> asIndex = new HashMap<>();
    private Pipe<?, E> lastPipe;

    public Gremlin(final Iterator<S> starts) {
        this.setStarts(new HolderIterator<>(this, starts));
    }

    public Gremlin(final Iterable<S> starts) {
        this(starts.iterator());
    }

    public Pipe setStarts(final Iterator<Holder<S>> starts) {
        this.pipes.add(0, new FilterPipe<S>(this, s -> true).setStarts(starts));
        this.lastPipe = this.pipes.get(this.pipes.size() - 1);
        return this;
    }

    public void addStart(final Holder<S> start) {
        this.pipes.get(0).addStart(start);
    }

    public <P extends Pipeline> P addPipe(final Pipe pipe) {
        pipe.setStarts(this.lastPipe);
        this.lastPipe = pipe;
        this.pipes.add(pipe);
        return (P) this;
    }

    public boolean hasNext() {
        return this.lastPipe.hasNext();
    }

    public Holder<E> next() {
        return this.lastPipe.next();
    }

    public int getAs(final String key) {
        if (!this.asIndex.containsKey(key))
            throw new IllegalStateException("The named pipe does not exist");
        return this.asIndex.get(key);
    }

    public <P extends GremlinPipeline> P as(final String key) {
        if (this.asIndex.containsKey(key))
            throw new IllegalStateException("The named pipe already exists");
        this.asIndex.put(key, pipes.size() - 1);
        return (P) this;
    }

    public <P extends GremlinPipeline> P back(final String key) {
        return (P) this.addPipe(new MapPipe<E, Object>(this, o -> o.getPath(key)));
    }

    public <P extends GremlinPipeline> P loop(final String key, final Predicate<Holder> whilePredicate, final Predicate<Holder> emitPredicate) {
        return this.addPipe(new MapPipe<E, Object>(this, o -> {
            o.incrLoops();
            if (whilePredicate.test(o)) {
                final Holder holder = new Holder(this, o.get(), o);
                holder.incrLoops();
                this.pipes.get(this.getAs(key)).addStart(holder);
                if (emitPredicate.test(o))
                    return o.get();
                else
                    return NO_OBJECT;
            } else {
                return o.get();
            }
        }));
    }
}
