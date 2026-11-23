package com.cnhsk.community.game;

import com.cnhsk.learning.api.GameCode;

/** Lets a contest round start only while its contest is open and for the contest's own game. */
public interface GameContestGate {
    void requireOpen(long contestId, GameCode code);
}
