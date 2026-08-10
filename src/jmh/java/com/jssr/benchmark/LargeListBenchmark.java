package com.jssr.benchmark;

import com.jssr.core.JssrComponent;
import com.jssr.core.compiler.JssrPrecompiler;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class LargeListBenchmark {

    public record UserItem(long id, String name, String email) implements JssrComponent {
        @Override
        public String template() {
            return "<tr><td>${id}</td><td>${name}</td><td>${email}</td></tr>";
        }
    }

    public record UserTable(List<UserItem> rows) implements JssrComponent {
        @Override
        public String template() {
            return """
                <table>
                    <thead><tr><th>ID</th><th>Name</th><th>Email</th></tr></thead>
                    <tbody>
                        @for (row : rows)
                            ${row}
                        @end
                    </tbody>
                </table>
                """;
        }
    }

    private UserTable table100;

    @Setup
    public void setup() {
        List<UserItem> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            list.add(new UserItem(i, "User " + i, "user" + i + "@example.com"));
        }
        table100 = new UserTable(list);
        JssrPrecompiler.compile(UserItem.class);
        JssrPrecompiler.compile(UserTable.class);
    }

    @Benchmark
    public String render100RowsInterpreted() {
        return table100.render();
    }

    @Benchmark
    public String render100RowsPrecompiled() {
        return table100.renderPrecompiled();
    }
}
