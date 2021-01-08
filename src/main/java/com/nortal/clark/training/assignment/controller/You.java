package com.nortal.clark.training.assignment.controller;

import com.nortal.clark.training.assignment.model.*;

import java.awt.geom.Line2D;
import java.util.List;

public class You {

    private final TrainingResult trainingResult = new TrainingResult();
    private List<Position> targetsToCapture;
    private static final long RACE_TIMEOUT_MILLIS = 10 * 60 * 1000;
    public static final double WATER_DRAG_THRESHOLD = 1.6;
    public static final int FRAME_SIZE_MILLIS = 500;
    public static final int TARGET_PROXIMITY_THRESHOLD = 2;

    public VoiceCommand getNextStep(Clark clark, CityMap cityMap) {

        if (targetsToCapture == null) {
            targetsToCapture = cityMap.getTargets();
        }
        updateTrainingResult(clark.getPosition(), clark, cityMap);
        VoiceCommand voiceCommand = new VoiceCommand(Direction.SOUTH, SpeedLevel.L0_RUNNING_HUMAN);


        double timeSpentSeconds = FRAME_SIZE_MILLIS / 1000.0;
        calculateReachedPosition(clark, voiceCommand, timeSpentSeconds);
        calculateAchievedSpeeds(clark, voiceCommand, timeSpentSeconds);

        updateTrainingResult(clark.getPosition(), clark, cityMap);
        Position targetToCapture = targetsToCapture.get(0);

        int diffX = Math.abs(targetToCapture.x - clark.getPosition().x);
        int diffY = Math.abs(targetToCapture.y - clark.getPosition().y);

        SpeedLevel horizontalSpeedLevel = thinkOfSpeedLevel(diffX, clark.getHorizontal());
        SpeedLevel verticalSpeedLevel = thinkOfSpeedLevel(diffY, clark.getVertical());

        if (diffX < 2 && diffY < 2) {
            if (isAllTargetsCaptured(cityMap)) {
                updateTrainingResult(clark.getPosition(), clark, cityMap);
            } else {
                captureTargetsOnDistanceTravelled(clark.getPosition(), clark, cityMap);
                trainingResult.addCapturedTarget(targetsToCapture.get(0));
                targetsToCapture.remove(0);
            }


        } else if (targetToCapture.x > clark.getPosition().x) {
            voiceCommand = new VoiceCommand(Direction.EAST, horizontalSpeedLevel);
        } else if (targetToCapture.y > clark.getPosition().y) {
            voiceCommand = new VoiceCommand(Direction.NORTH, verticalSpeedLevel);
        } else if (targetToCapture.x < clark.getPosition().x) {
            voiceCommand = new VoiceCommand(Direction.WEST, horizontalSpeedLevel);
        } else if (targetToCapture.y < clark.getPosition().y) {
            voiceCommand = new VoiceCommand(Direction.SOUTH, verticalSpeedLevel);
        }

        System.out.println(voiceCommand);
        return voiceCommand;
    }

    //<<SOLUTION START>>
    private SpeedLevel thinkOfSpeedLevel(int distanceDiff, double speed) {
        double realSpeed = speed - getDragAcceleration(speed);
        double timeCheck = distanceDiff / realSpeed;

        // 11.1313 m/s
        if (timeCheck <= 11 && timeCheck > 0)
            return SpeedLevel.L1_TRAIN;
        // 340 m/s
        if (timeCheck >= 34 && timeCheck <= 11 && timeCheck > 0)
            return SpeedLevel.L2_SUB_SONIC;
        // 1700 m/s
        if (timeCheck >= 170 && timeCheck <= 34 && timeCheck > 0)
            return SpeedLevel.L3_SUPER_SONIC;
        // 370k m/s
        return SpeedLevel.L4_MACH_9350;
    }


    private void updateTrainingResult(Position positionBeforeCommand, Clark clark, CityMap cityMap) {
        if (trainingResult.getTrainingStatus() == null) {
            trainingResult.startTraining();
        }

        trainingResult.addTrainingTime(FRAME_SIZE_MILLIS);

        if (trainingResult.getTrainingTime() > RACE_TIMEOUT_MILLIS) {
            trainingResult.setTrainingStatus(TrainingStatus.TIMEOUT);
            return;
        }

        if (!cityMap.isWithinCity(clark.getPosition())) {
            trainingResult.setTrainingStatus(TrainingStatus.OUTSIDE_CITY);
            return;
        }

        // Collect any targets on the distance travelled
        captureTargetsOnDistanceTravelled(positionBeforeCommand, clark, cityMap);

        if (isAllTargetsCaptured(cityMap)) {
            trainingResult.setTrainingStatus(TrainingStatus.COMPLETED);
        }

    }

    private boolean isAllTargetsCaptured(CityMap cityMap) {
        // Check if all targets collected
        boolean allTargetsCaptured = true;
        for (Position target : cityMap.getTargets()) {
            if (!trainingResult.getCapturedTargets().contains(target)) {
                allTargetsCaptured = false;
                break;
            }
        }
        return allTargetsCaptured;
    }

    private void captureTargetsOnDistanceTravelled(Position positionBeforeCommand, Clark clark, CityMap cityMap) {
        for (Position target : cityMap.getTargets()) {
            double passingDistance = Line2D.ptSegDist(
                    positionBeforeCommand.x, positionBeforeCommand.y,
                    clark.getPosition().x, clark.getPosition().y,
                    target.x, target.y);

            if (passingDistance < TARGET_PROXIMITY_THRESHOLD) {
                System.err.println("Target captured " + target);
                trainingResult.addCapturedTarget(target);
            }
        }
    }

    private void calculateReachedPosition(Clark clark, VoiceCommand command, double timeSpentSeconds) {

        int displacementX;
        int displacementY;

        Direction direction = command.getDirection();
        SpeedLevel speedLevel = command.getSpeedLevel();

        double speedX = clark.getHorizontal();
        double speedY = clark.getVertical();

        if (speedLevel.equals(SpeedLevel.L0_RUNNING_HUMAN)) {
            //If Clark is not putting any effort, then both directions affected by drag only
            displacementX = calculateDisplacementWithDrag(speedX, timeSpentSeconds);
            displacementY = calculateDisplacementWithDrag(speedY, timeSpentSeconds);

        } else if (Direction.NORTH == direction || Direction.SOUTH == direction) {
            //If Clark is applying force vertically, then X affected by drag, Y by Clark's power
            displacementX = calculateDisplacementWithDrag(speedX, timeSpentSeconds);
            displacementY = calculateDisplacement(speedY, command, timeSpentSeconds);

        } else if (Direction.WEST == direction || Direction.EAST == direction) {
            //If Clark is applying force horizontally, then Y affected by drag, X by Clark's power
            displacementX = calculateDisplacement(speedX, command, timeSpentSeconds);
            displacementY = calculateDisplacementWithDrag(speedY, timeSpentSeconds);

        } else {
            throw new IllegalArgumentException("Voice command " + command + " invalid");
        }

        clark.getPosition().translate(displacementX, displacementY);
    }

    private int calculateDisplacement(double currentSpeed, VoiceCommand command, double timeSpentSeconds) {
        double acceleration = calculateAcceleration(currentSpeed, command.getDirection().getAccelerationModifier(), command.getSpeedLevel());
        return calculateDisplacement(currentSpeed, acceleration, timeSpentSeconds);
    }

    private int calculateDisplacement(double currentSpeed, double acceleration, double timeSpentSeconds) {
        double exactDisplacement = currentSpeed * timeSpentSeconds + 1.0 / 2.0 * acceleration * Math.pow(timeSpentSeconds, 2);
        return (int) Math.round(exactDisplacement);
    }

    /*
     * Drag is a special kind of acceleration that has always an opposite direction to current movement
     * Depending on current movement direction we either increase or decrease the position.
     * We also compensate for overshoot since drag eventually pulls Clark to a stop
     */
    private int calculateDisplacementWithDrag(double currentSpeed, double timeSpentSeconds) {

        double dragAcceleration = getDragAcceleration(currentSpeed);
        int displacement = calculateDisplacement(currentSpeed, dragAcceleration, timeSpentSeconds);

        //compensate overshooting. Drag does not make Clark to change direction
        if (currentSpeed == 0 || (currentSpeed < 0 && displacement > 0) || (currentSpeed > 0 && displacement < 0)) {
            displacement = 0;
        }

        return displacement;
    }

    /**
     * Calculates Clark's speed with the power Clark has put that he achieves in given time (milliseconds)
     */
    void calculateAchievedSpeeds(Clark clark, VoiceCommand command, double timeSpentSeconds) {

        double newSpeedX;
        double newSpeedY;

        double speedX = clark.getHorizontal();
        double speedY = clark.getVertical();

        if (command.getSpeedLevel().equals(SpeedLevel.L0_RUNNING_HUMAN)) {
            //If Clark is not putting any effort, then both directions affected by drag only
            newSpeedX = calculateNewSpeedWithDrag(speedX, timeSpentSeconds);
            newSpeedY = calculateNewSpeedWithDrag(speedY, timeSpentSeconds);

        } else if (Direction.NORTH.equals(command.getDirection()) || Direction.SOUTH.equals(command.getDirection())) {
            //If Clark is applying force vertically, then X affected by drag, Y by Clark's power
            newSpeedX = calculateNewSpeedWithDrag(speedX, timeSpentSeconds);
            newSpeedY = calculateNewSpeed(speedY, command, timeSpentSeconds);

        } else if (Direction.WEST.equals(command.getDirection()) || Direction.EAST.equals(command.getDirection())) {
            //If Clark is applying force horizontally, then Y affected by drag, X by Clark's power
            newSpeedX = calculateNewSpeed(speedX, command, timeSpentSeconds);
            newSpeedY = calculateNewSpeedWithDrag(speedY, timeSpentSeconds);

        } else {
            throw new IllegalArgumentException("Voice command " + command + " invalid");
        }

        //speed = speed + acceleration * timeSpentSeconds;
        clark.setHorizontal(newSpeedX);
        clark.setVertical(newSpeedY);
    }

    /*
     * Drag is a special kind of acceleration that has always an opposite direction to current movement
     * Depending on current movement direction we either increase or decrease the speed.
     * We also compensate for overshoot since the ultimate target speed for drag equals 0
     */
    double calculateNewSpeedWithDrag(double currentSpeed, double timeSpentSeconds) {

        double dragAcceleration = getDragAcceleration(currentSpeed);
        double newSpeed = calculateNewSpeed(currentSpeed, dragAcceleration, timeSpentSeconds);

        //compensate overshooting. Drag does not make Clark to change direction
        if (currentSpeed == 0 || (currentSpeed < 0 && newSpeed > 0) || (currentSpeed > 0 && newSpeed < 0)) {
            newSpeed = 0;
        }

        return newSpeed;
    }

    private double calculateNewSpeed(double currentSpeed, VoiceCommand command, double timeSpentSeconds) {
        double acceleration = calculateAcceleration(currentSpeed, command.getDirection().getAccelerationModifier(), command.getSpeedLevel());
        return calculateNewSpeed(currentSpeed, acceleration, timeSpentSeconds);
    }

    double calculateNewSpeed(double currentSpeed, double acceleration, double timeSpentSeconds) {
        return currentSpeed + acceleration * timeSpentSeconds;
    }

    /**
     * Calculated directional acceleration of Clark and level of speed.
     * For example if Clark is moving EAST with L2_SUB_SONIC then acceleration is positive
     * if Clark is moving WEST with L2_SUB_SONIC then acceleration is negative
     *
     * @param accelerationModifier indicates the direction of acceleration applied at given level.
     *                             The speed and direction are always on the same axis
     */
    double calculateAcceleration(double currentSpeed, int accelerationModifier, SpeedLevel speedLevel) {
        if (accelerationModifier != -1 && accelerationModifier != 1) {
            throw new IllegalArgumentException("Acceleration modifier must be either -1 or 1");
        }

        double acceleration = accelerationModifier * speedLevel.getAcceleration();
        if (currentSpeed == 0) {
            //If current speed for given direction is 0 then soon to apply drag is opposite to acceleration
            //If acceleration does not exceed threshold, Clark does not move
            if (speedLevel.getAcceleration() <= WATER_DRAG_THRESHOLD) {
                return 0;
            }

            double dragAcceleration = -accelerationModifier * WATER_DRAG_THRESHOLD;
            return acceleration + dragAcceleration;
        } else {
            //Otherwise drag is applied depending on the current movement direction
            double dragAcceleration = getDragAcceleration(currentSpeed);
            return acceleration + dragAcceleration;
        }

    }

    /**
     * Calculates directional acceleration of water depending on current speed indicating movement.
     * Drag affects Clark always in the opposite direction to his movement
     * <p>
     * Formula: x = 1.6 + v²/200
     * This gives us vmax = √480 = approximately 21.908902
     */
    double getDragAcceleration(double currentSpeed) {
        // 0 if currentSpeed = 0
        double dragDirectionalModifier = -Math.signum(currentSpeed);
        //
        // Clark's maximum speed is x²
        double waterDrag = WATER_DRAG_THRESHOLD + (Math.pow(currentSpeed, 2) / 200);
        return dragDirectionalModifier * waterDrag;
    }


}
