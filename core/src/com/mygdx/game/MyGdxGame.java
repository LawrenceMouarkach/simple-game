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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.mygdx.game.characters.Koala;

import static com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT;
import static com.mygdx.game.characters.Koala.KoalaState.DAMPING;
import static com.mygdx.game.characters.Koala.KoalaState.HEIGHT;
import static com.mygdx.game.characters.Koala.KoalaState.MAX_VELOCITY;
import static com.mygdx.game.characters.Koala.KoalaState.State.JUMPING;
import static com.mygdx.game.characters.Koala.KoalaState.State.STANDING;
import static com.mygdx.game.characters.Koala.KoalaState.State.WALKING;
import static com.mygdx.game.characters.Koala.KoalaState.WIDTH;

public class MyGdxGame implements ApplicationListener {

    private TiledMap map;
    private OrthogonalTiledMapRenderer renderer;
    private OrthographicCamera camera;
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
    private Array<Rectangle> tiles = new Array<>();

    private static final float GRAVITY = -2.5f;

    @Override
    public void create() {
        // load the koala frames, split them, and assign them to Animations
        Texture koalaTexture = new Texture("core/assets/player/koalio.png");
        TextureRegion[] regions = TextureRegion.split(koalaTexture, 18, 26)[0];
        stand = STANDING.animation();
        jump = JUMPING.animation();
        walk = WALKING.animation();
        walk.setPlayMode(Animation.PlayMode.LOOP_PINGPONG);

        // figure out the width and height of the koala for collision
        // detection and rendering by converting a koala frames pixel
        // size into world units (1 unit == 16 pixels)
        WIDTH = 1 / 16f * regions[0].getRegionWidth();
        HEIGHT = 1 / 16f * regions[0].getRegionHeight();

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


        Koala.KoalaState.State.JUMPING.buttonPress(koala);
        Koala.KoalaState.State.WALKING.buttonPress(koala);
        // apply gravity if we are falling
        koala.velocity.add(0, GRAVITY);

        // clamp the velocity to the maximum, x-axis only
        if (Math.abs(koala.velocity.x) > MAX_VELOCITY) {
            koala.velocity.x = Math.signum(koala.velocity.x) * MAX_VELOCITY;
        }

        Koala.KoalaState.State.STANDING.buttonPress(koala);

        // multiply by delta time so we know how far we go
        // in this frame
        koala.velocity.scl(deltaTime);

        // perform collision detection & response, on each axis, separately
        // if the koala is moving right, check the tiles to the right of it's
        // right bounding box edge, otherwise check the ones to the left
        Rectangle koalaRect = (Rectangle) rectPool.obtain();
        koalaRect.set(koala.position.x, koala.position.y, WIDTH, HEIGHT);
        int startX, startY, endX, endY;
        if (koala.velocity.x > 0) {
            startX = endX = (int) (koala.position.x + WIDTH + koala.velocity.x);
        } else {
            startX = endX = (int) (koala.position.x + koala.velocity.x);
        }
        startY = (int) (koala.position.y);
        endY = (int) (koala.position.y + HEIGHT);
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
            startY = endY = (int) (koala.position.y + HEIGHT + koala.velocity.y);
        } else {
            startY = endY = (int) (koala.position.y + koala.velocity.y);
        }
        startX = (int) (koala.position.x);
        endX = (int) (koala.position.x + WIDTH);
        getTiles(startX, startY, endX, endY, tiles);
        koalaRect.y += koala.velocity.y;
        for (Rectangle tile : tiles) {
            if (koalaRect.overlaps(tile)) {
                // we actually reset the koala y-position here
                // so it is just below/above the tile we collided with
                // this removes bouncing 🙂
                if (koala.velocity.y > 0) {
                    koala.position.y = tile.y - HEIGHT;
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
        koala.velocity.x *= DAMPING;

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
            batch.draw(frame, koala.position.x, koala.position.y, WIDTH, HEIGHT);
        } else {
            batch.draw(frame, koala.position.x + WIDTH, koala.position.y, -WIDTH, HEIGHT);
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
        if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE) && isPaused()) { //TODO for mobile screen as well


        }

    }

    @Override
    public void resume() {
        if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE) && !isPaused()) { //TODO for mobile screen as well


        }

    }

    private static boolean isPaused() {
        return false; //TODO meaningful isPaused
    }
}