package io.github.h7kz.proofstone.build.matrix;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.options.Option;

/**
 * Runs {@code CiMatrix} from {@code proofstone-core} with the {@code --scope} option (T-M3-07, ADR-0008). The class
 * is in build-logic so the Gradle option is declared once; the matrix logic itself lives beside the planner it
 * reuses. The arguments are set in {@code proofstone.gates.gradle.kts}, so this class holds only the scope.
 */
public abstract class PrintMatrix extends JavaExec {

    private String scope = "pr";

    @Input
    public String getScope() {
        return scope;
    }

    /** @param scope {@code pr} (required rows) or {@code nightly} (every non-retired row) */
    @Option(option = "scope", description = "Matrix scope: pr (required rows) or nightly (every non-retired row).")
    public void setScope(String scope) {
        this.scope = scope;
    }
}
