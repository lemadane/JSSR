package com.jssr.benchmark;

import com.jssr.core.JssrComponent;
import com.jssr.core.compiler.JssrPrecompiler;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class SimpleComponentBenchmark {

    public record SimpleUser(String name, String role) implements JssrComponent {
        @Override
        public String template() {
            return "<article><h2>${name}</h2><p>${role}</p></article>";
        }
    }

    private SimpleUser user;

    @Setup
    public void setup() {
        user = new SimpleUser("Lemuel", "Architect");
        JssrPrecompiler.compile(SimpleUser.class);
    }

    @Benchmark
    public String renderInterpreted() {
        return user.render();
    }

    @Benchmark
    public String renderPrecompiled() {
        return user.renderPrecompiled();
    }
}
