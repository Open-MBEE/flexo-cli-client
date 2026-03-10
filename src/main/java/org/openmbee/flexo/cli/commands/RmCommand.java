package org.openmbee.flexo.cli.commands;

import org.openmbee.flexo.cli.FlexoCLI;
import org.openmbee.flexo.cli.util.ConsoleUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Rm command - Remove elements from the model
 */
@Command(
        name = "rm",
        description = "Remove elements from the model",
        mixinStandardHelpOptions = true
)
public class RmCommand extends BaseCommand {

    @ParentCommand
    protected FlexoCLI parent;

    @Override
    protected FlexoCLI getParentCli() {
        return parent;
    }

    @Option(names = {"-i", "--iri"}, description = "IRI of element to remove")
    private String iri;

    @Option(names = {"-p", "--pattern"}, description = "SPARQL delete pattern")
    private String pattern;

    @Option(names = {"-f", "--file"}, description = "File containing IRIs or patterns")
    private String file;

    @Parameters(index = "0", arity = "0..1", description = "IRI or pattern (alternative to --iri)")
    private String iriParam;

    @Override
    protected void executeCommand() throws Exception {
        ConsoleUtil.warn("The 'rm' command is not yet fully implemented");
        ConsoleUtil.info("This command would remove elements from the model using SPARQL DELETE operations");
        ConsoleUtil.info("");
        ConsoleUtil.info("Planned usage:");
        ConsoleUtil.info("  flexo rm --iri <element-iri>        # Remove specific element");
        ConsoleUtil.info("  flexo rm --pattern '<sparql>'       # Remove using SPARQL pattern");
        ConsoleUtil.info("  flexo rm --file <file>              # Remove elements from file");
        ConsoleUtil.info("");
        ConsoleUtil.info("This requires SPARQL UPDATE support in the MMS API");

        throw new CommandExecutionException("Command not yet implemented", 1);
    }
}
