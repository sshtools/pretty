package com.sshtools.pretty.pricli;

import java.util.List;

import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.ParseResult;

public interface PricliCompleter {

    void complete(LineReader reader, ParsedLine line, List<Candidate> candidates, ParseResult parseResult, ArgSpec lastArg);
}
