package com.jssr.benchmark;

import com.jssr.core.JssrComponent;
import com.jssr.core.compiler.JssrPrecompiler;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class ControlFlowBenchmark {

    public record UserStatusCard(String name, boolean active, String role) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="user-card">
                    <h3>${name}</h3>
                    @if (active)
                        <span class="badge active">Online</span>
                    @else
                        <span class="badge inactive">Offline</span>
                    @end
                    <p>Role: ${role}</p>
                </div>
                """;
        }
    }

    private UserStatusCard card;

    @Setup
    public void setup() {
        card = new UserStatusCard("Lemuel", true, "Lead Architect");
        JssrPrecompiler.compile(UserStatusCard.class);
    }

    @Benchmark
    public String renderInterpreted() {
        return card.render();
    }

    @Benchmark
    public String renderPrecompiled() {
        return card.renderPrecompiled();
    }
}
