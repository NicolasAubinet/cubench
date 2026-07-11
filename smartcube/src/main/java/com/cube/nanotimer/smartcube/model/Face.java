package com.cube.nanotimer.smartcube.model;

/**
 * A cube face. Smart cubes report quarter turns only; whole-cube rotations are not
 * moves (the driver derives those from the gyro stream instead).
 */
public enum Face { U, D, L, R, F, B }
