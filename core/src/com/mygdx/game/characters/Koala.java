package com.mygdx.game.characters;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

public class Koala {

    public final Vector2 position = new Vector2();
    public final Vector2 velocity = new Vector2();
    public KoalaState.State state = KoalaState.State.WALKING;
    public float stateTime = 0;
    public boolean facesRight = true;
    public boolean grounded = false;

    public static class KoalaState {

        public static float WIDTH;
        public static float HEIGHT;
        public static float MAX_VELOCITY = 10f;
        public static float JUMP_VELOCITY = 40f;
        public static float DAMPING = 0.87f;

        public enum State {

            STANDING {
                @Override
                public void buttonPress(final Koala koala) {
                    if (Math.abs(koala.velocity.x) < 1) {
                        koala.velocity.x = 0;
                        if (koala.grounded)
                            koala.state = STANDING;
                    }
                }

                @Override
                public Animation<?> animation() {
                    return new Animation<>(0, regions[0]);
                }
            },
            WALKING {
                @Override
                public void buttonPress(final Koala koala) {
                    if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A) || isTouched(0, 0.25f)) {
                        koala.velocity.x = -MAX_VELOCITY;
                        if (koala.grounded)
                            koala.state = WALKING;
                        koala.facesRight = false;
                    }
                    if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D) || isTouched(0.25f, 0.5f)) {
                        koala.velocity.x = MAX_VELOCITY;
                        if (koala.grounded)
                            koala.state = WALKING;
                        koala.facesRight = true;
                    }
                }

                @Override
                public Animation animation() {
                    return new Animation<>(0.15f, regions[2], regions[3], regions[4]);
                }
            },
            JUMPING {
                @Override
                public void buttonPress(final Koala koala) {
                    if ((Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W) || isTouched(0.75f, 1)) && koala.grounded) {
                        koala.velocity.y += JUMP_VELOCITY;
                        koala.state = JUMPING;
                        koala.grounded = false;
                    }

                }

                @Override
                public Animation animation() {
                    return new Animation<>(0, regions[1]);
                }
            },
            ATTACKING {
                @Override
                public void buttonPress(final Koala koala) {

                }

                @Override
                public Animation animation() {
                    return null;
                }
            };

            public abstract void buttonPress(final Koala koala);

            public abstract Animation animation();

            private static Texture koalaTexture = new Texture("core/assets/player/koalio.png");
            private static TextureRegion[] regions = TextureRegion.split(koalaTexture, 18, 26)[0];
        }
    }

    private static boolean isTouched(float startX, float endX) {
        // check if any finge is touch the area between startX and endX
        // startX/endX are given between 0 (left edge of the screen) and 1 (right edge of the screen)
        for (int i = 0; i < 2; i++) {
            float x = Gdx.input.getX() / (float) Gdx.graphics.getWidth();
            if (Gdx.input.isTouched(i) && (x >= startX && x <= endX)) {
                return true;
            }
        }
        return false;
    }
}