package fr.cnrs.lacito.liftpatchbox.timeline;

/**
 * What a recording does after a script that is rejected (a static error) or that fails
 * (a command fails and the script is rolled back).
 */
public enum RecordPolicy {

    /**
     * Go on with the next script, which starts from what the scripts before committed: each
     * script is its own transaction. The default.
     */
    CONTINUE,

    /**
     * Stop the series there: the scripts after it are not recorded at all. For a series whose
     * scripts depend on each other, where a later script cannot be trusted to mean what it
     * says once an earlier one did not apply. The failed script keeps its positions (not
     * committed), so the state it reached before failing can still be sought.
     */
    STOP_AT_FIRST_FAILURE
}
