package mypackage;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.util.*;
// part 1 Aiman Fatima

public class Frogger extends Application {

    // ── Constants ────────────────────────────────────────────────────────────
    static final int W    = 480;
    static final int H    = 560;   // extra 40 px for HUD bar
    static final int TILE = 40;
    static final int COLS = W / TILE;

    // ── Difficulty ───────────────────────────────────────────────────────────
    enum Difficulty { EASY, MEDIUM, HARD }
    Difficulty difficulty = Difficulty.MEDIUM;

    // ── Screens ──────────────────────────────────────────────────────────────
    enum Screen { MENU, PLAYING, OVER }
    Screen screen = Screen.MENU;
    int menuSel  = 0;

    // ── Night Mode ───────────────────────────────────────────────────────────
    boolean nightMode = false;

    // ── High Score
    int highScore = 0;

    // ── Screen Shake
    double shakeX = 0, shakeY = 0;
    int    shakeTicks = 0;
    static final int SHAKE_DURATION = 18;

    // ── Water Animation ──────────────────────────────────────────────────────
    double waterOffset = 0;          // scrolls water stripe pattern
    List<Ripple> ripples = new ArrayList<>();

    static class Ripple {
        double x, y, radius, alpha;
        Ripple(double x, double y) { this.x = x; this.y = y; this.radius = 4; this.alpha = 0.7; }
        boolean update() { radius += 1.2; alpha -= 0.035; return alpha <= 0; }
    }

    // ── Death Animation ──────────────────────────────────────────────────────
    boolean deathAnimating = false;
    int     deathTick      = 0;
    double  deathX, deathY;
    static final int DEATH_FRAMES = 40;

    // ── Particle System ──────────────────────────────────────────────────────
    List<Particle> particles = new ArrayList<>();

    static class Particle {
        double x, y, vx, vy, alpha, size;
        Color  color;
        Particle(double x, double y, Color c) {
            this.x = x; this.y = y; this.color = c;
            double angle = Math.random() * Math.PI * 2;
            double speed = 1 + Math.random() * 3;
            vx = Math.cos(angle) * speed;
            vy = Math.sin(angle) * speed;
            alpha = 1.0; size = 4 + Math.random() * 4;
        }
        boolean update() { x += vx; y += vy; vy += 0.1; alpha -= 0.03; return alpha <= 0; }
    }

    // ── Animated Title ────────────────────────────────────────────────────────
    double titleBounce = 0;
    double titleAlpha  = 1.0;
    boolean titleFadeDir = false;

    // ── Inner: Car ───────────────────────────────────────────────────────────
    static class Car {
        double x, y, speed, width;
        Color  color;
        Car(double x, double y, double speed, double width, Color color) {
            this.x = x; this.y = y; this.speed = speed; this.width = width; this.color = color;
        }
        void update() {
            x += speed;
            if (speed > 0 && x > W + width) x = -width;
            if (speed < 0 && x + width < 0) x = W;
        }
        boolean hits(double fx, double fy) {
            return fx + 30 > x && fx < x + width && fy + 32 > y && fy < y + TILE - 4;
        }
    }

    // ── Inner: Log ───────────────────────────────────────────────────────────
    static class Log {
        double x, y, speed, width;
        Log(double x, double y, double speed, double width) {
            this.x = x; this.y = y; this.speed = speed; this.width = width;
        }
        void update() {
            x += speed;
            if (speed > 0 && x > W + width) x = -width;
            if (speed < 0 && x + width < 0) x = W;
        }
        boolean onLog(double fx, double fy) {
            return fx + 28 > x && fx + 4 < x + width && fy + 10 > y && fy < y + TILE - 8;
        }
    }

    // ── Game State ───────────────────────────────────────────────────────────
    double         frogX, frogY, logRideSpeed;
    List<Car>      cars     = new ArrayList<>();
    List<Log>      logs     = new ArrayList<>();
    Set<Integer>   safeSpots = new HashSet<>();
    int            score = 0, lives = 3;

    // ── Difficulty helpers ───────────────────────────────────────────────────
    double carSpd() {
        switch (difficulty) {
            case EASY:
                return 1.5;
            case MEDIUM:
                return 2.5;
            default:
                return 3.8;
        }
    }
    double logSpd() {
        switch (difficulty) {
            case EASY:
                return 1.2;
            case MEDIUM:
                return 1.8;
            default:
                return 2.8;
        }
    }
    double logW() {
        switch (difficulty) {
            case EASY:
                return 110;
            case MEDIUM:
                return 85;
            default:
                return 62;
        }
    }
    int initLives() {
        switch (difficulty) {
            case EASY:
                return 5;
            case MEDIUM:
                return 3;
            default:
                return 1;
        }
    }
    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(W, H);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Scale transform so game coords stay constant while window resizes
        javafx.scene.transform.Scale scale = new javafx.scene.transform.Scale(1, 1, 0, 0);
        canvas.getTransforms().add(scale);

        Scene scene = new Scene(new Group(canvas), W, H);
        scene.setOnKeyPressed(e -> handleKey(e.getCode()));

        scene.widthProperty().addListener((o, ov, nv) -> {
            scale.setX(nv.doubleValue() / W);
            scale.setY(scene.getHeight() / H);
        });
        scene.heightProperty().addListener((o, ov, nv) -> {
            scale.setX(scene.getWidth() / W);
            scale.setY(nv.doubleValue() / H);
        });

        /* Set application icon via canvas snapshot (no external file ) */
        new AnimationTimer() {
            public void handle(long now) {
                if (screen == Screen.PLAYING) update();
                else if (screen == Screen.MENU) updateMenu();
                draw(gc);
            }
        }.start();

        stage.setScene(scene);
        stage.setTitle("Frogger  | Arrow keys · N = Night Mode");
        stage.setResizable(true);
        stage.show();
    }

    // part 2 Irza Hashim

    // ── Key Handler ──────────────────────────────────────────────────────────
    void handleKey(KeyCode k) {
        // Night mode toggle works everywhere
        if (k == KeyCode.N) { nightMode = !nightMode; return; }

        if (screen == Screen.MENU) {
            if (k == KeyCode.UP)    menuSel = (menuSel + 2) % 3;
            if (k == KeyCode.DOWN)  menuSel = (menuSel + 1) % 3;
            if (k == KeyCode.ENTER || k == KeyCode.SPACE) startGame();
            return;
        }
        if (screen == Screen.OVER) {
            if (k == KeyCode.R) { screen = Screen.MENU; menuSel = 0; }
            return;
        }
        // Block movement during death animation
        if (deathAnimating) return;

        if (k == KeyCode.UP)    frogY -= TILE;
        if (k == KeyCode.DOWN)  frogY += TILE;
        if (k == KeyCode.LEFT)  frogX -= TILE;
        if (k == KeyCode.RIGHT) frogX += TILE;
        if (k == KeyCode.R)     { screen = Screen.MENU; menuSel = 0; return; }

        frogX = Math.max(0, Math.min(W - TILE, frogX));
        frogY = Math.max(0, Math.min(480, frogY));



        playBeep(880, 30);   // hop sound

        if (frogY < TILE) checkTopRow();
    }

    void startGame() {
        difficulty  = menuSel == 0 ? Difficulty.EASY
                : menuSel == 1 ? Difficulty.MEDIUM : Difficulty.HARD;
        score       = 0;
        lives       = initLives();
        safeSpots.clear();

        ripples.clear();
        particles.clear();
        resetFrog();
        setupLevel();
        screen = Screen.PLAYING;
    }

    void resetFrog() {
        frogX = W / 2.0 - 20;
        frogY = 480;
        deathAnimating = false;
        deathTick      = 0;
    }

    // ── Setup Level ──────────────────────────────────────────────────────────
    void setupLevel() {
        cars.clear();
        logs.clear();
        double cs = carSpd(), ls = logSpd(), lw = logW();

        // Cars (road rows 240–400)
        double[][] cd = {
                {  0, 240,  cs,      50}, {200, 240,  cs,      50}, {380, 240,  cs,      50},
                { 50, 280, -cs*.8,   65}, {260, 280, -cs*.8,   65},
                {  0, 320,  cs*1.2,  45}, {180, 320,  cs*1.2,  45}, {340, 320,  cs*1.2,  45},
                { 80, 360, -cs,      55}, {290, 360, -cs,      55},
                {  0, 400,  cs*.7,   70}, {220, 400,  cs*.7,   70},
                {120, 240,  cs*1.3,  38}, {320, 280, -cs*1.1,  42}   // extra cars for length
        };
        Color[] cc = {Color.RED, Color.ORANGE, Color.YELLOW, Color.CYAN, Color.MAGENTA,
                Color.web("#ff6b6b"), Color.web("#b8f542"), Color.web("#42d4f4")};
        for (int i = 0; i < cd.length; i++)
            cars.add(new Car(cd[i][0], cd[i][1], cd[i][2], cd[i][3], cc[i % cc.length]));

        // Logs (river rows 40–200)
        double[][] ld = {
                {  0,  40,  ls,     lw},   {160,  40,  ls,     lw},   {330,  40,  ls,     lw},
                { 60,  80, -ls*.8,  lw+20},{280,  80, -ls*.8,  lw+20},
                {  0, 120,  ls*1.1, lw-10},{170, 120,  ls*1.1, lw-10},{330, 120,  ls*1.1, lw-10},
                { 50, 160, -ls,     lw+10},{270, 160, -ls,     lw+10},
                {  0, 200,  ls*.9,  lw},   {190, 200,  ls*.9,  lw}
        };
        for (double[] d : ld) logs.add(new Log(d[0], d[1], d[2], d[3]));


    }

    // ── Update ───────────────────────────────────────────────────────────────
    void update() {
        // Handle death animation
        if (deathAnimating) {
            deathTick++;
            spawnDeathParticles();
            if (deathTick >= DEATH_FRAMES) {
                deathAnimating = false;
                lives--;
                if (lives <= 0) { screen = Screen.OVER; updateHighScore(); }
                else resetFrog();
            }
            updateParticles();
            return;
        }

        // Screen shake countdown
        if (shakeTicks > 0) {
            shakeX = (Math.random() - 0.5) * 6;
            shakeY = (Math.random() - 0.5) * 6;
            shakeTicks--;
        } else { shakeX = 0; shakeY = 0; }

        // Move entities
        for (Car  c : cars)    c.update();
        for (Log  l : logs)    l.update();


        // Water animation
        waterOffset = (waterOffset + 0.4) % (TILE * 2);

        // Spawn random ripple
        if (Math.random() < 0.04)
            ripples.add(new Ripple(Math.random() * W, 40 + Math.random() * 200));
        ripples.removeIf(Ripple::update);

        // River logic
        boolean inRiver = frogY >= 40 && frogY <= 200;
        logRideSpeed = 0;
        if (inRiver) {
            boolean safe = false;
            for (Log l : logs) {
                if (l.onLog(frogX, frogY)) { logRideSpeed = l.speed; safe = true; break; }
            }

            if (!safe) { triggerDeath(); return; }
            frogX += logRideSpeed;
            if (frogX < 0 || frogX > W - TILE) { triggerDeath(); return; }
        }

        // Road collision
        if (frogY >= 240 && frogY <= 430) {
            for (Car c : cars) {
                if (c.hits(frogX, frogY)) { triggerDeath(); return; }
            }
        }

        updateParticles();
    }

    /** Start the death animation instead of instant respawn. */
    void triggerDeath() {
        if (deathAnimating) return;
        deathAnimating = true;
        deathTick      = 0;
        deathX         = frogX;
        deathY         = frogY;
        shakeTicks     = SHAKE_DURATION;
        playBeep(200, 200);   // low thud
        // Burst of particles
        for (int i = 0; i < 20; i++)
            particles.add(new Particle(frogX + 16, frogY + 16,
                    Color.hsb(Math.random() * 360, 1, 1)));
    }

    /** Spawn a few particles each death frame. */
    void spawnDeathParticles() {
        if (deathTick % 4 == 0)
            particles.add(new Particle(deathX + 16 + (Math.random()-0.5)*20,
                    deathY + 16 + (Math.random()-0.5)*20,
                    Color.web("#ff4444")));
    }

    // part 3 Ayesha Afzal

    void updateParticles() { particles.removeIf(Particle::update); }

    void checkTopRow() {
        int slot = (int)(frogX / TILE);
        if (slot >= 0 && slot < COLS && !safeSpots.contains(slot)) {
            safeSpots.add(slot);
            score += 100;
            playBeep(1400, 60);
        }
        resetFrog();
        if (safeSpots.size() >= 5) { screen = Screen.OVER; updateHighScore(); }
    }

    void updateHighScore() { if (score > highScore) highScore = score; }

    // ── Menu animation tick ───────────────────────────────────────────────────
    void updateMenu() {
        titleBounce += 0.07;
        if (titleFadeDir) titleAlpha += 0.02; else titleAlpha -= 0.02;
        if (titleAlpha <= 0.3) titleFadeDir = true;
        if (titleAlpha >= 1.0) titleFadeDir = false;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** Draw the frog body at (fx, fy). Supports a death-spin effect. */
    void drawFrog(GraphicsContext gc, double fx, double fy, double alpha, double angle) {
        gc.save();
        gc.setGlobalAlpha(alpha);
        gc.translate(fx + 16, fy + 16);
        gc.rotate(angle);
        gc.translate(-16, -16);

        // Body
        gc.setFill(Color.web("#27ae60"));
        gc.fillOval(4, 6, 24, 22);
        gc.setFill(Color.web("#2ecc71"));
        gc.fillOval(7, 8, 18, 16);

        // Eyes
        gc.setFill(Color.WHITE);
        gc.fillOval(6, 4, 8, 8);
        gc.fillOval(18, 4, 8, 8);
        gc.setFill(Color.BLACK);
        gc.fillOval(8, 5, 4, 5);
        gc.fillOval(20, 5, 4, 5);
        // Eye shine
        gc.setFill(Color.WHITE);
        gc.fillOval(9, 5, 2, 2);
        gc.fillOval(21, 5, 2, 2);

        // Legs
        gc.setFill(Color.web("#27ae60"));
        gc.fillOval(0,  16, 10, 14);  // left-mid
        gc.fillOval(22, 16, 10, 14);  // right-mid
        gc.fillOval(3,  24, 10, 10);  // left-back
        gc.fillOval(19, 24, 10, 10);  // right-back

        // Mouth
        gc.setStroke(Color.web("#145a32"));
        gc.setLineWidth(1);
        gc.strokeArc(9, 14, 14, 8, 200, -20, javafx.scene.shape.ArcType.OPEN);

        gc.restore();
    }



    void draw(GraphicsContext gc) {
        if (screen == Screen.MENU) { drawMenu(gc); return; }

        // Apply screen shake
        gc.save();
        gc.translate(shakeX, shakeY);

        // ── Background ───────────────────────────────────────────────────────
        Color grassCol  = nightMode ? Color.web("#0d2b05") : Color.web("#2d5a1b");
        Color riverCol  = nightMode ? Color.web("#051a33") : Color.web("#1a6b8a");
        Color roadCol   = nightMode ? Color.web("#1a1a1a") : Color.web("#555555");

        gc.setFill(grassCol);
        gc.fillRect(0, 0, W, H);
        gc.setFill(riverCol);
        gc.fillRect(0, 40, W, 200);
        gc.setFill(roadCol);
        gc.fillRect(0, 240, W, 200);

        // ── Animated Water Stripes ────────────────────────────────────────────
        drawAnimatedWater(gc);

        // ── Road Lane Lines ───────────────────────────────────────────────────
        gc.setStroke(nightMode ? Color.web("#555") : Color.web("#888"));
        gc.setLineDashes(12, 8);
        gc.setLineWidth(1.5);
        for (int r = 1; r < 5; r++) gc.strokeLine(0, 240 + r * 40, W, 240 + r * 40);
        gc.setLineDashes(0);

        // ── Safe-Spot Pads (top row) ──────────────────────────────────────────
        drawSafeSpots(gc);

        // ── Ripples ───────────────────────────────────────────────────────────
        for (Ripple rp : ripples) {
            gc.setStroke(Color.color(1, 1, 1, rp.alpha));
            gc.setLineWidth(1.2);
            gc.strokeOval(rp.x - rp.radius, rp.y - rp.radius, rp.radius * 2, rp.radius * 2);
        }

        // ── Logs ─────────────────────────────────────────────────────────────
        for (Log l : logs) {
            gc.setFill(Color.web("#8B4513"));
            gc.fillRoundRect(l.x, l.y + 4, l.width, TILE - 10, 12, 12);
            gc.setFill(Color.web("#A0522D"));
            gc.fillRoundRect(l.x + 4, l.y + 7, l.width - 8, TILE - 16, 8, 8);
            // wood grain lines
            gc.setStroke(Color.web("#6b3410"));
            gc.setLineWidth(0.8);
            for (int g = 0; g < (int)(l.width / 18); g++)
                gc.strokeLine(l.x + 10 + g * 18, l.y + 6, l.x + 10 + g * 18, l.y + TILE - 8);
        }



        // ── Cars ─────────────────────────────────────────────────────────────
        for (Car c : cars) drawCar(gc, c);



        // ── Particles ─────────────────────────────────────────────────────────
        for (Particle p : particles) {
            gc.setFill(Color.color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), p.alpha));
            gc.fillOval(p.x - p.size / 2, p.y - p.size / 2, p.size, p.size);
        }

        // ── Frog ─────────────────────────────────────────────────────────────
        if (deathAnimating) {
            // Spin and fade the frog during death animation
            double progress = (double) deathTick / DEATH_FRAMES;
            double angle = progress * 720;           // two full rotations
            double alpha = 1.0 - progress;
            drawFrog(gc, deathX, deathY, alpha, angle);
        } else {
            drawFrog(gc, frogX, frogY, 1.0, 0);
        }

        // ── Night Mode overlay ────────────────────────────────────────────────
        if (nightMode) drawNightOverlay(gc);

        // ── HUD ──────────────────────────────────────────────────────────────
        drawHUD(gc);

        gc.restore();   // end shake transform

        // ── Game Over Overlay ─────────────────────────────────────────────────
        if (screen == Screen.OVER) drawGameOverOverlay(gc);
    }

    // ── Animated Water ────────────────────────────────────────────────────────
    void drawAnimatedWater(GraphicsContext gc) {
        // Subtle scrolling lighter stripes in the river band
        gc.setFill(Color.color(1, 1, 1, nightMode ? 0.03 : 0.06));
        for (int row = 0; row < 5; row++) {
            double baseY = 40 + row * 40 + 14;
            double startX = (waterOffset + row * 30) % (TILE * 2) - TILE * 2;
            while (startX < W) {
                gc.fillRoundRect(startX, baseY, TILE * 1.5, 6, 4, 4);
                startX += TILE * 2;
            }
        }
    }

    // Part 4- Eman

    // ── Safe Spots ────────────────────────────────────────────────────────────
    void drawSafeSpots(GraphicsContext gc) {
        for (int i = 0; i < COLS; i++) {
            boolean safe = safeSpots.contains(i);
            gc.setFill(safe ? Color.LIMEGREEN : Color.web(nightMode ? "#143d1a" : "#1e8b3c"));
            gc.fillRoundRect(i * TILE + 2, 4, TILE - 4, TILE - 4, 8, 8);
        }
    }

    // ── Draw Car ──────────────────────────────────────────────────────────────
    void drawCar(GraphicsContext gc, Car c) {
        // Body
        gc.setFill(c.color);
        gc.fillRoundRect(c.x, c.y + 5, c.width, TILE - 12, 10, 10);
        // Wheels
        gc.setFill(Color.web("#222"));
        gc.fillRect(c.x + 5,            c.y + TILE - 14, 12, 8);
        gc.fillRect(c.x + c.width - 17, c.y + TILE - 14, 12, 8);
    }

    // ── Night Mode Overlay ────────────────────────────────────────────────────
    void drawNightOverlay(GraphicsContext gc) {
        // Dark vignette + blue tint
        gc.setFill(Color.color(0, 0, 0.1, 0.45));
        gc.fillRect(0, 0, W, H);
        // Stars in the sky band (top)
        gc.setFill(Color.color(1, 1, 1, 0.7));
        long seed = 42;
        for (int i = 0; i < 30; i++) {
            seed = seed * 1103515245L + 12345;
            double sx = ((seed >> 3) & 0xFFFF) % W;
            double sy = ((seed >> 5) & 0xFFFF) % 38;
            gc.fillOval(sx, sy, 2, 2);
        }
        // Moon
        gc.setFill(Color.color(1.0, 0.98, 0.85, 0.9));
        gc.fillOval(W - 55, 6, 24, 24);
        gc.setFill(Color.color(0.05, 0.05, 0.1, 0.9));
        gc.fillOval(W - 50, 6, 24, 24);   // crescent cutout
    }

    // ── HUD ──────────────────────────────────────────────────────────────────
    void drawHUD(GraphicsContext gc) {
        gc.setFill(Color.color(0, 0, 0, nightMode ? 0.75 : 0.5));
        gc.fillRect(0, H - 40, W, 40);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        gc.fillText("Score: " + score, 8, H - 14);
        gc.fillText("Best: " + highScore, 105, H - 14);
        gc.fillText(difficulty.toString(), 205, H - 14);



        // Lives as frog emojis
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        for (int i = 0; i < lives; i++)
            gc.fillText("🐸", W - 30 - i * 28, H - 12);

        // Night indicator
        if (nightMode) {
            gc.setFill(Color.color(0.8, 0.8, 1.0, 0.8));
            gc.setFont(Font.font("Arial", 11));
            gc.fillText("🌙 NIGHT", 8, H - 28);
        }
    }

    // ── Game Over / Win Overlay ───────────────────────────────────────────────
    void drawGameOverOverlay(GraphicsContext gc) {
        boolean won = safeSpots.size() >= 5;
        gc.setFill(Color.color(0, 0, 0, 0.7));
        gc.fillRect(0, 0, W, H);

        // Panel
        gc.setFill(Color.color(won ? 0.1 : 0.3, won ? 0.35 : 0.05, 0.1, 0.9));
        gc.fillRoundRect(60, 150, W - 120, 220, 20, 20);
        gc.setStroke(won ? Color.LIMEGREEN : Color.RED);
        gc.setLineWidth(2.5);
        gc.strokeRoundRect(60, 150, W - 120, 220, 20, 20);

        gc.setFill(won ? Color.LIMEGREEN : Color.RED);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(won ? "YOU WIN! 🎉" : "GAME OVER", W / 2.0, 210);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        gc.fillText("Score: " + score, W / 2.0, 250);
        gc.setFont(Font.font("Arial", 14));
        gc.fillText("Best: " + highScore, W / 2.0, 275);

        gc.setFill(Color.web("#aaa"));
        gc.setFont(Font.font("Arial", 13));
        gc.fillText("Press R to return to menu", W / 2.0, 320);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    // ── Menu ─────────────────────────────────────────────────────────────────
    void drawMenu(GraphicsContext gc) {
        // Uniform background — single fill, no visible mid-screen seam
        gc.setFill(nightMode ? Color.web("#060f02") : Color.web("#0d2106"));
        gc.fillRect(0, 0, W, H);

        // Animated stars in night menu
        if (nightMode) {
            gc.setFill(Color.color(1, 1, 1, 0.6));
            Random rng = new Random(99L);
            for (int i = 0; i < 40; i++)
                gc.fillOval(rng.nextInt(W), rng.nextInt(H), 2, 2);
        }

        // Bouncing title
        double bounce = Math.sin(titleBounce) * 8;
        gc.setFill(Color.LIMEGREEN);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 48));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("🐸 FROGGER", W / 2.0, 100 + bounce);

        // Difficulty options
        String[] labels = {
                "EASY   – 5 lives, wide logs",
                "MEDIUM – 3 lives, normal speed",
                "HARD   – 1 life, narrow logs, fast"
        };
        Color[] cols = {Color.LIMEGREEN, Color.YELLOW, Color.ORANGERED};
        for (int i = 0; i < 3; i++) {
            boolean sel = (i == menuSel);
            gc.setFill(sel ? cols[i].deriveColor(0, 1, 1, 0.2) : Color.color(1, 1, 1, 0.05));
            gc.fillRoundRect(60, 170 + i * 80, 360, 60, 14, 14);
            gc.setStroke(sel ? cols[i] : Color.web("#335533"));
            gc.setLineWidth(sel ? 2.5 : 1);
            gc.strokeRoundRect(60, 170 + i * 80, 360, 60, 14, 14);
            gc.setFill(sel ? cols[i] : Color.web("#88aa88"));
            gc.setFont(Font.font("Arial", FontWeight.BOLD, sel ? 16 : 14));
            gc.fillText((sel ? "▶  " : "     ") + labels[i], W / 2.0, 208 + i * 80);
        }

        // Controls legend
        gc.setFill(Color.web("#557755"));
        gc.setFont(Font.font("Arial", 12));
        gc.fillText("↑ ↓ select  •  ENTER / SPACE start  •  R restart  •  N night mode", W / 2.0, 442);

        // High score
        if (highScore > 0) {
            gc.setFill(Color.GOLD);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 13));
            gc.fillText("🏆 Best: " + highScore, W / 2.0, 468);
        }

        gc.setTextAlign(TextAlignment.LEFT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sound (javax.sound.sampled – pure Java, no extra files)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Play a simple beep tone using PCM synthesis.
     * @param freqHz  frequency in Hz
     * @param millis  duration in milliseconds
     */
    void playBeep(double freqHz, int millis) {
        new Thread(() -> {
            try {
                int sampleRate = 22050;
                int numSamples = sampleRate * millis / 1000;
                byte[] buf = new byte[numSamples];
                for (int i = 0; i < numSamples; i++) {
                    double t = (double) i / sampleRate;
                    double envelope = Math.min(1.0, Math.min(t * 80, (numSamples - i) / (double) sampleRate * 80));
                    buf[i] = (byte)(envelope * 60 * Math.sin(2 * Math.PI * freqHz * t));
                }
                javax.sound.sampled.AudioFormat fmt = new javax.sound.sampled.AudioFormat(sampleRate, 8, 1, true, false);
                javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                        javax.sound.sampled.SourceDataLine.class, fmt);
                javax.sound.sampled.SourceDataLine line =
                        (javax.sound.sampled.SourceDataLine) javax.sound.sampled.AudioSystem.getLine(info);
                line.open(fmt); line.start();
                line.write(buf, 0, buf.length);
                line.drain(); line.close();
            } catch (Exception ignored) { /* no audio device – silently skip */ }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) { launch(args); }}