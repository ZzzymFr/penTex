package use_case.phase_portrait;

// fresh = brand new portrait (new card), false = same one panned / zoomed / pinned
public interface PhasePortraitOutputBoundary {
    void preparePortraitView(PhasePortraitOutputData data, boolean fresh);

    // null clears the readout
    void prepareTraceView(PhaseTraceOutputData data);

    void prepareFailView(String error);

    // what a find came back with, null = take it off the portrait
    void prepareLookupView(use_case.lookup.LookupOutputData data);
}
