package org.acme;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class Game {

    private static final double CAR_WIDTH = 8.0;
    private static final double CAR_HEIGHT = 6.0;
    private final String id;
    private Map<String, Player> players = new HashMap<>();
    private Ball ball;

    // Définir des variables pour la vitesse initiale et le plafond de vitesse
    private static final double INITIAL_SPEED = 0.5;
    private static final double MAX_SPEED = 2.0; // Par exemple, vous pouvez ajuster ce nombre selon vos besoins

    // Ajouter des variables pour suivre les mouvements récents et la vitesse actuelle de chaque joueur
    private Map<String, String> recentMovements = new HashMap<>();
    private Map<String, Double> speeds = new HashMap<>();


    int duration = 120;

    private ScheduledExecutorService timer;
    private boolean gameStarted = false;


    public Game(String id) {
        this.id = id;
        this.ball = new Ball();
    }

    public String getId() {
        return id;
    }

    public void addPlayer(String playerId) {
        Player player = new Player(playerId, new Car(0, 0));
        players.put(player.getId(), player);
    }

    public void removePlayer(String playerId) {
        players.remove(playerId);
    }

    public Player getPlayer(String playerId) {
        return players.get(playerId);
    }

    public Map<String, Player> getPlayers() {
        return players;
    }

    public Ball getBall() {
        return ball;
    }

    public void sendMessageToAllPlayers(String message) {
        GameService gameService = GameService.getInstance();
        gameService.getGameWebSocket().sendMessageToAll(message);
    }


    public void processMessageGame(String message) {

        Player player1 = players.values().stream().findFirst().get();
        Player player2 = players.values().stream().skip(1).findFirst().get();

        switch (message) {
            case "start":
                ball.setX(50);
                ball.setY(50);

                //Set player cars to initial position one to 5,50, and other to 95,50

                player1.getCar().setX(5);
                player1.getCar().setY(50);

                player2.getCar().setX(95);
                player2.getCar().setY(50);

                //Send message to all players
                sendMessageToAllPlayers("game:started");
                sendMessageToAllPlayers("ball:" + ball.getX() + "," + ball.getY());
                sendMessageToAllPlayers("player1:" + player1.getCar().getX() + "," + player1.getCar().getY());
                sendMessageToAllPlayers("player1:rotateRight");
                sendMessageToAllPlayers("player2:" + player2.getCar().getX() + "," + player2.getCar().getY());
                sendMessageToAllPlayers("player2:rotateLeft");

                // Initialiser le minuteur
                timer = Executors.newSingleThreadScheduledExecutor();
                timer.scheduleAtFixedRate(() -> {
                    if (gameStarted) {
                        sendMessageToAllPlayers("timer:" + duration);
                        duration--;
                        if (duration < 0) {
                            // Arrêter le jeu et le minuteur lorsque le temps est écoulé
                            timer.shutdownNow();
                            sendMessageToAllPlayers("game:ended");
                        }
                    }
                }, 0, 1000, TimeUnit.MILLISECONDS); // Utiliser 1000 millisecondes au lieu de TimeUnit.SECONDS

                gameStarted = true;
                break;
        }
    }


    public void processMessageMove(String message) {
        String direction = "";

        if (message.contains("-")) {
            //decompose message to get before and after ":"
            String[] parts = message.split("-");
            message = parts[0];
            direction = parts[1];
        }

        Player player1 = players.values().stream().findFirst().get();
        Player player2 = players.values().stream().skip(1).findFirst().get();

        switch (message) {
            case "player1":
                handlePlayerMovement(player1, player2, direction, "player1", "player2");
                break;
            case "player2":
                handlePlayerMovement(player2, player1, direction, "player2", "player1");
                break;
        }
    }

    private void handlePlayerMovement(Player player, Player otherPlayer, String direction, String playerId, String otherPlayerId) {
        // Mettre à jour la vitesse actuelle du joueur
        Double currentSpeed = speeds.getOrDefault(playerId, INITIAL_SPEED);

        // Augmenter la vitesse si la direction est la même que précédemment
        if (direction.equals(recentMovements.getOrDefault(playerId, ""))) {
            if (currentSpeed < MAX_SPEED) {
                currentSpeed += 0.25;
            }
        } else {
            // Réinitialiser la vitesse si la direction a changé
            currentSpeed = INITIAL_SPEED;
        }

        // Limiter la vitesse au plafond
        currentSpeed = Math.min(currentSpeed, MAX_SPEED);

        // Mettre à jour les mouvements récents et la vitesse actuelle du joueur
        recentMovements.put(playerId, direction);
        speeds.put(playerId, currentSpeed);

        // Mettre à jour la position du joueur en fonction de la vitesse
        double newX = player.getCar().getX();
        double newY = player.getCar().getY();

        switch (direction) {
            case "ArrowUp":
                sendMessageToAllPlayers(playerId + ":rotateUp");
                newY += currentSpeed;
                break;
            case "ArrowDown":
                sendMessageToAllPlayers(playerId + ":rotateDown");
                newY -= currentSpeed;
                break;
            case "ArrowLeft":
                sendMessageToAllPlayers(playerId + ":rotateLeft");
                newX -= currentSpeed;
                break;
            case "ArrowRight":
                sendMessageToAllPlayers(playerId + ":rotateRight");
                newX += currentSpeed;
                break;
        }

        // Calculer les dimensions de la boîte englobante pour chaque voiture en fonction de son orientation
        double width1 = CAR_WIDTH;
        double height1 = CAR_HEIGHT;
        double width2 = CAR_WIDTH;
        double height2 = CAR_HEIGHT;

        String orientation1 = recentMovements.getOrDefault(playerId, "");

        String orientation2 = recentMovements.getOrDefault(otherPlayerId, "");

        if (orientation1.equals("ArrowUp") || orientation1.equals("ArrowDown")) {
            width1 = CAR_HEIGHT;
            height1 = CAR_WIDTH;
        }

        if (orientation2.equals("ArrowUp") || orientation2.equals("ArrowDown")) {
            width2 = CAR_HEIGHT;
            height2 = CAR_WIDTH;
        }

        // Vérifier si le déplacement est valide (dans les limites du terrain et pas de collision entre les joueurs)
        if (isValidPosition(newX, newY)) {
            if (handleCollision(player, otherPlayer, playerId , otherPlayerId)) {
                // Il y a collision, ajuster les positions des deux joueurs en conséquence

                System.out.println("Collision between players");


                // Réinitialiser la vitesse du joueur courant
                speeds.put(playerId, INITIAL_SPEED);
                speeds.put(otherPlayerId, INITIAL_SPEED);

            } else {

                System.out.println("No collision between players");

                player.getCar().setX(newX);
                player.getCar().setY(newY);

                // Envoyer les mises à jour aux joueurs
                sendMessageToAllPlayers(playerId + ":" + newX + "," + newY);

                //Envoyer la largeur des voitures pour les deux joueurs
                sendMessageToAllPlayers("player1,width:" + width1 + "," + height1);
                sendMessageToAllPlayers("player2,width:" + width2 + "," + height2);

            }

        }
    }

    // Vérifier si la position est valide (dans les limites du terrain)
    private boolean isValidPosition(double x, double y) {
        return x >= 0 && x <= 100 && y >= 0 && y <= 100;
    }

    private boolean handleCollision(Player player, Player otherPlayer, String playerId, String otherPlayerId) {
        // Récupérer les positions et les dimensions des voitures
        double x1 = player.getCar().getX();
        double y1 = player.getCar().getY();
        String orientation1 = recentMovements.getOrDefault(playerId, "");
        double width1 = CAR_WIDTH;
        double height1 = CAR_HEIGHT;

        double x2 = otherPlayer.getCar().getX();
        double y2 = otherPlayer.getCar().getY();
        String orientation2 = recentMovements.getOrDefault(otherPlayerId, "");
        double width2 = CAR_WIDTH;
        double height2 = CAR_HEIGHT;

        if (orientation1.equals("ArrowUp") || orientation1.equals("ArrowDown")) {
            width1 = CAR_HEIGHT;
            height1 = CAR_WIDTH;
        }

        if (orientation2.equals("ArrowUp") || orientation2.equals("ArrowDown")) {
            width2 = CAR_HEIGHT;
            height2 = CAR_WIDTH;
        }

        // Calculer les coordonnées des bords des voitures
        double left1 = x1 - width1 / 2;
        double right1 = x1 + width1 / 2;
        double top1 = y1 - height1 / 2;
        double bottom1 = y1 + height1 / 2;

        double left2 = x2 - width2 / 2;
        double right2 = x2 + width2 / 2;
        double top2 = y2 - height2 / 2;
        double bottom2 = y2 + height2 / 2;

        // Vérifier si les voitures sont en collision
        boolean collision = !(right1 < left2 || left1 > right2 || bottom1 < top2 || top1 > bottom2);

        if (collision) {
            // Calculer le chevauchement
            double overlapX = Math.min(right1, right2) - Math.max(left1, left2);
            double overlapY = Math.min(bottom1, bottom2) - Math.max(top1, top2);

            // Faire reculer les voitures sur l'axe avec le plus grand chevauchement
            if (Math.abs(overlapX) >= Math.abs(overlapY)) {
                double recul = overlapX > 0 ? -overlapX : overlapX;
                player.getCar().setY(player.getCar().getY() - recul / 2);
                otherPlayer.getCar().setY(otherPlayer.getCar().getY() + recul / 2);
            } else {
                double recul = overlapY > 0 ? -overlapY : overlapY;
                player.getCar().setX(player.getCar().getX() - recul / 2);
                otherPlayer.getCar().setX(otherPlayer.getCar().getX() + recul / 2);
            }

            // Envoyer les mises à jour aux joueurs
            sendMessageToAllPlayers(playerId + ":" + player.getCar().getX() + "," + player.getCar().getY());
            sendMessageToAllPlayers(otherPlayerId + ":" + otherPlayer.getCar().getX() + "," + otherPlayer.getCar().getY());
        }

        return collision;
    }



}

