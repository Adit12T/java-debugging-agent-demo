package io.github.adit12t.debugagent;

import io.github.adit12t.debugagent.Model.Diagnosis;
import io.github.adit12t.debugagent.Model.ErrorGroup;
import io.github.adit12t.debugagent.Model.SourceSnippet;

/** Explains an error group: root cause, suggested fix, and a regression test draft. */
public interface Analyst {

    Diagnosis diagnose(ErrorGroup group, SourceSnippet snippet);
}
