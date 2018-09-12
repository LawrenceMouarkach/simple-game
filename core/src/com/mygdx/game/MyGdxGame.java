package com.mygdx.game;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

import static com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT;

public class MyGdxGame implements ApplicationListener {

    /**
     * The player character, has state and state time,
     */
    static class Koala {
        static float WIDTH;
        static float HEIGHT;
        static float MAX_VELOCITY = 10f;
        static float JUMP_VELOCITY = 40f;
        static float DAMPING = 0.87f;

        enum State {
            STANDING, WALKING, JUMPING, ATTACKING
        }

        final Vector2 position = new Vector2();
        final Vector2 velocity = new Vector2();
        State state = State.WALKING;
        float stateTime = 0;
        boolean facesRight = true;
        boolean grounded = false;
    }

    private TiledMap map;
    private OrthogonalTiledMapRenderer renderer;
    private OrthographicCamera camera;
    private Texture koalaTexture;
    private Animation stand;
    private Animation walk;
    private Animation jump;
    private Koala koala;
    private Pool rectPool = new Pool() {
        @Override
        protected Rectangle newObject() {
            return new Rectangle();
        }
    };
    private Array<Rectangle> tiles = new Array();

    private static final float GRAVITY = -2.5f;

    @Override
    public void create() {
        // load the koala frames, split them, and assign them to Animations
        koalaTexture = new Texture("core/assets/koalio.png");
        TextureRegion[] regions = TextureRegion.split(koalaTexture, 18, 26)[0];
        stand = new Animation(0, regions[0]);
        jump = new Animation(0, regions[1]);
        walk = new Animation(0.15f, regions[2], regions[3], regions[4]);
        walk.setPlayMode(Animation.PlayMode.LOOP_PINGPONG);

        // figure out the width and height of the koala for collision
        // detection and rendering by converting a koala frames pixel
        // size into world units (1 unit == 16 pixels)
        Koala.WIDTH = 1 / 16f * regions[0].getRegionWidth();
        Koala.HEIGHT = 1 / 16f * regions[0].getRegionHeight();

        // load the map, set the unit scale to 1/16 (1 unit == 16 pixels)
        map = new TmxMapLoader().load("core/assets/level1.tmx");
        renderer = new OrthogonalTiledMapRenderer(map, 1 / 16f);

        // create an orthographic camera, shows us 30x20 units of the world
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 30, 20);
        camera.update();

        // create the Koala we want to move around the world
        koala = new Koala();
        koala.position.set(20, 20);
    }

    @Override
    public void render() {
        // clear the screen
        Gdx.gl.glClearColor(0.7f, 0.7f, 1.0f, 1);
        Gdx.gl.glClear(GL_COLOR_BUFFER_BIT);

        // get the delta time
        float deltaTime = Gdx.graphics.getDeltaTime();

        // update the koala (process input, collision detection, position update)
        updateKoala(deltaTime);

        // let the camera follow the koala, x-axis only
        camera.position.x = koala.position.x;
        camera.update();

        // set the tile map rendere view based on what the
        // camera sees and render the map
        renderer.setView(camera);
        renderer.render();

        // render the koala
        renderKoala();
    }

    private void updateKoala(float deltaTime) {
        if (deltaTime == 0)
            return;
        koala.stateTime += deltaTime;

        // check input and apply to velocity & state
        if ((Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W) || isTouched(0.75f, 1)) && koala.grounded) {
            koala.velocity.y += Koala.JUMP_VELOCITY;
            koala.state = Koala.State.JUMPING;
            koala.grounded = false;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A) || isTouched(0, 0.25f)) {
            koala.velocity.x = -Koala.MAX_VELOCITY;
            if (koala.grounded)
                koala.state = Koala.State.WALKING;
            koala.facesRight = false;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D) || isTouched(0.25f, 0.5f)) {
            koala.velocity.x = Koala.MAX_VELOCITY;
            if (koala.grounded)
                koala.state = Koala.State.WALKING;
            koala.facesRight = true;
        }

        // apply gravity if we are falling
        koala.velocity.add(0, GRAVITY);

        // clamp the velocity to the maximum, x-axis only
        if (Math.abs(koala.velocity.x) > Koala.MAX_VELOCITY) {
            koala.velocity.x = Math.signum(koala.velocity.x) * Koala.MAX_VELOCITY;
        }

        // clamp the velocity to 0 if it's < 1, and set the state to standign
        if (Math.abs(koala.velocity.x) < 1) {
            koala.velocity.x = 0;
            if (koala.grounded)
                koala.state = Koala.State.STANDING;
        }

        // multiply by delta time so we know how far we go
        // in this frame
        koala.velocity.scl(deltaTime);

        // perform collision detection & response, on each axis, separately
        // if the koala is moving right, check the tiles to the right of it's
        // right bounding box edge, otherwise check the ones to the left
        Rectangle koalaRect = (Rectangle) rectPool.obtain();
        koalaRect.set(koala.position.x, koala.position.y, Koala.WIDTH, Koala.HEIGHT);
        int startX, startY, endX, endY;
        if (koala.velocity.x > 0) {
            startX = endX = (int) (koala.position.x + Koala.WIDTH + koala.velocity.x);
        } else {
            startX = endX = (int) (koala.position.x + koala.velocity.x);
        }
        startY = (int) (koala.position.y);
        endY = (int) (koala.position.y + Koala.HEIGHT);
        getTiles(startX, startY, endX, endY, tiles);
        koalaRect.x += koala.velocity.x;
        for (Rectangle tile : tiles) {
            if (koalaRect.overlaps(tile)) {
                koala.velocity.x = 0;
                break;
            }
        }
        koalaRect.x = koala.position.x;

        // if the koala is moving upwards, check the tiles to the top of it's
        // top bounding box edge, otherwise check the ones to the bottom
        if (koala.velocity.y > 0) {
            startY = endY = (int) (koala.position.y + Koala.HEIGHT + koala.velocity.y);
        } else {
            startY = endY = (int) (koala.position.y + koala.velocity.y);
        }
        startX = (int) (koala.position.x);
        endX = (int) (koala.position.x + Koala.WIDTH);
        getTiles(startX, startY, endX, endY, tiles);
        koalaRect.y += koala.velocity.y;
        for (Rectangle tile : tiles) {
            if (koalaRect.overlaps(tile)) {
                // we actually reset the koala y-position here
                // so it is just below/above the tile we collided with
                // this removes bouncing 🙂
                if (koala.velocity.y > 0) {
                    koala.position.y = tile.y - Koala.HEIGHT;
                    // we hit a block jumping upwards, let's destroy it!
                    TiledMapTileLayer layer = (TiledMapTileLayer) map.getLayers().get(2);
                    layer.setCell((int) tile.x, (int) tile.y, null);
                } else {
                    koala.position.y = tile.y + tile.height;
                    // if we hit the ground, mark us as grounded so we can jump
                    koala.grounded = true;
                }
                koala.velocity.y = 0;
                break;
            }
        }
        rectPool.free(koalaRect);

        // unscale the velocity by the inverse delta time and set
        // the latest position
        koala.position.add(koala.velocity);
        koala.velocity.scl(1 / deltaTime);

        // Apply damping to the velocity on the x-axis so we don't
        // walk infinitely once a key was pressed
        koala.velocity.x *= Koala.DAMPING;

    }

    private boolean isTouched(float startX, float endX) {
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

    private void getTiles(int startX, int startY, int endX, int endY, Array tiles) {
        TiledMapTileLayer layer = (TiledMapTileLayer) map.getLayers().get(2);
        rectPool.freeAll(tiles);
        tiles.clear();
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                TiledMapTileLayer.Cell cell = layer.getCell(x, y);
                if (cell != null) {
                    Rectangle rect = (Rectangle) rectPool.obtain();
                    rect.set(x, y, 1, 1);
                    tiles.add(rect);
                }
            }
        }
    }

    private void renderKoala() {
        // based on the koala state, get the animation frame
        TextureRegion frame = null;
        switch (koala.state) {
            case STANDING:
                frame = (TextureRegion) stand.getKeyFrame(koala.stateTime);
                break;
            case WALKING:
                frame = (TextureRegion) walk.getKeyFrame(koala.stateTime);
                break;
            case JUMPING:
                frame = (TextureRegion) jump.getKeyFrame(koala.stateTime);
                break;
        }

        // draw the koala, depending on the current velocity
        // on the x-axis, draw the koala facing either right
        // or left
        Batch batch = renderer.getBatch();
        batch.begin();
        if (koala.facesRight) {
            batch.draw(frame, koala.position.x, koala.position.y, Koala.WIDTH, Koala.HEIGHT);
        } else {
            batch.draw(frame, koala.position.x + Koala.WIDTH, koala.position.y, -Koala.WIDTH, Koala.HEIGHT);
        }
        batch.end();
    }

    @Override
    public void dispose() {
    }

    @Override
    public void resize(int width, int height) {
        // TODO Auto-generated method stub

    }

    @Override
    public void pause() {
        // TODO Auto-generated method stub

    }

    @Override
    public void resume() {
        // TODO Auto-generated method stub

    }
}