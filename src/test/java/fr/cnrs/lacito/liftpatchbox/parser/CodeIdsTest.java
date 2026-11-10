package fr.cnrs.lacito.liftpatchbox.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.liftpatchbox.error.ErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.ModelErrorCode;
import fr.cnrs.lacito.liftpatchbox.error.ModelWarningCode;
import fr.cnrs.lacito.liftpatchbox.error.WarningCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The error and warning codes declared by the core and by the language are
 * identified by their id alone: no id may be declared twice.
 */
class CodeIdsTest {

    @Test
    void errorCodeIdsAreUnique() {
        List<ErrorCode> all = Stream.<ErrorCode>concat(
            Stream.of(ModelErrorCode.values()), Stream.of(SyntaxErrorCode.values())).toList();
        Set<String> seen = new HashSet<>();
        for (ErrorCode code : all) {
            assertTrue(seen.add(code.id()), "error code declared twice: " + code.id());
        }
    }

    @Test
    void warningCodeIdsAreUnique() {
        List<WarningCode> all = Stream.<WarningCode>concat(
            Stream.of(ModelWarningCode.values()), Stream.of(SyntaxWarningCode.values())).toList();
        Set<String> seen = new HashSet<>();
        for (WarningCode code : all) {
            assertTrue(seen.add(code.id()), "warning code declared twice: " + code.id());
        }
    }
}
