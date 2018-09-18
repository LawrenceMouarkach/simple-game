package com.mygdx.game.characters;

import com.badlogic.gdx.math.Vector2;

public class Koala {
    public static float WIDTH;
    public static float HEIGHT;
    public static float MAX_VELOCITY = 10f;
    public static float JUMP_VELOCITY = 40f;
    public static float DAMPING = 0.87f;

    public enum State {
        STANDING, WALKING, JUMPING, ATTACKING
    }

    public final Vector2 position = new Vector2();
    public final Vector2 velocity = new Vector2();
    public State state = State.WALKING;
    public float stateTime = 0;
    public boolean facesRight = true;
    public boolean grounded = false;
}
