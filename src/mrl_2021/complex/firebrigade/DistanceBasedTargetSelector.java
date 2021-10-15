package mrl_2021.complex.firebrigade;

import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import mrl_2021.ambulance.AmbulanceTarget;
import mrl_2021.util.Util;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

/**
 * @author Pooya Deldar Gohardani
 * Date: 2/21/13
 * Time: 1:49 PM
 */

/**
 * This implementation uses different distance parameters (such as distance form refuge to target, distance from agent to target and distance from partitions to target)to select best target and uses possible filters such as dead time filters
 */
public class DistanceBasedTargetSelector {

    //Map of previously found victimsID to its AmbulanceTarget object
    private Map<EntityID, AmbulanceTarget> targetsMap;
    private double threshold = Double.MAX_VALUE;// A threshold for selecting victim
    private int rescueRange;
    protected boolean isMapHuge = false;
    protected boolean isMapMedium = false;
    protected boolean isMapSmall = false;
    public static final double MEAN_VELOCITY_OF_MOVING = 31445.392;
    protected int minX, minY, maxX, maxY;
    private AmbulanceTarget previousTarget = null;
    private int clusterIndex;
    private Map<Integer, Pair<Integer, Integer>> centersMap;
    Map<Integer, Pair<Integer, Integer>> clusterCenterMap;

    private WorldInfo worldInfo;
    private ScenarioInfo scenarioInfo;
    private AgentInfo agentInfo;
    private Clustering clustering;
    private PathPlanning pathPlanning;

    public DistanceBasedTargetSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
//        super(ai, wi, si, moduleManager, developData);


        this.worldInfo = wi;
        this.scenarioInfo = si;
        this.agentInfo = ai;

        switch (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("SampleRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.clustering = moduleManager.getModule("SampleVictimSelector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("SampleRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.clustering = moduleManager.getModule("SampleVictimSelector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("SampleRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.clustering = moduleManager.getModule("SampleVictimSelector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
        }


        targetsMap = new HashMap<>();
        clusterCenterMap = new HashMap<>();
        calculateMapDimensions();
        verifyMap();
        initializeRescueRange();
    }


    private void calculateMapDimensions() {
        this.minX = Integer.MAX_VALUE;
        this.maxX = Integer.MIN_VALUE;
        this.minY = Integer.MAX_VALUE;
        this.maxY = Integer.MIN_VALUE;
        Pair<Integer, Integer> pos;
        List<StandardEntity> invalidEntities = new ArrayList<>();
        for (StandardEntity standardEntity : worldInfo.getAllEntities()) {
            pos = standardEntity.getLocation(worldInfo.getRawWorld());
            if (pos.first() == Integer.MIN_VALUE || pos.first() == Integer.MAX_VALUE || pos.second() == Integer.MIN_VALUE || pos.second() == Integer.MAX_VALUE) {
                invalidEntities.add(standardEntity);
                continue;
            }
            if (pos.first() < this.minX)
                this.minX = pos.first();
            if (pos.second() < this.minY)
                this.minY = pos.second();
            if (pos.first() > this.maxX)
                this.maxX = pos.first();
            if (pos.second() > this.maxY)
                this.maxY = pos.second();
        }
        if (!invalidEntities.isEmpty()) {
            System.out.println("##### WARNING: There is some invalid entities ====> " + invalidEntities.size());
        }
    }

    private int getMapWidth() {
        return maxX - minX;
    }

    private int getMapHeight() {
        return maxY - minY;
    }


    private void verifyMap() {

        double mapDimension = Math.hypot(getMapWidth(), getMapHeight());

        double rate = mapDimension / MEAN_VELOCITY_OF_MOVING;

        if (rate > 60) {
            isMapHuge = true;
        } else if (rate > 30) {
            isMapMedium = true;
        } else {
            isMapSmall = true;
        }


    }

    private void initializeRescueRange() {
        int perceptionLosMaxDistance = scenarioInfo.getPerceptionLosMaxDistance();
        if (isMapSmall) {
            rescueRange = perceptionLosMaxDistance * 2;
        } else if (isMapMedium) {
            rescueRange = perceptionLosMaxDistance * 4;
        } else { //if map is huge
            rescueRange = perceptionLosMaxDistance * 6;
        }
//        MrlPersonalData.VIEWER_DATA.setRescueRange(rescueRange);
    }


    /**
     * Finds best target between specified possible targetsMap
     *
     * @return best target to select
     */
    public EntityID nextTarget(Set<StandardEntity> victims) {

//        Clustering clustering = this.moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING);

//        if (this.clusterIndex == -1) {
        this.clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
//        }


        Collection<StandardEntity> elements = clustering.getClusterEntities(this.clusterIndex);

        calculateMapCenters(this.clusterIndex, elements);


        targetsMap.clear();

        if (previousTarget != null && !victims.contains(worldInfo.getEntity(previousTarget.getVictimID()))) {
            previousTarget = null;
        }


        refreshTargetsMap(victims, targetsMap);

        calculateDecisionParameters(victims, targetsMap);

        calculateVictimsCostValue();


        AmbulanceTarget bestTarget = null;
        bestTarget = findBestVictim(targetsMap, elements);

        //considering inertia for the current target to prevent loop in target selection
        if (previousTarget != null && victims.contains(worldInfo.getEntity(previousTarget.getVictimID()))) {
            if (bestTarget != null && !bestTarget.getVictimID().equals(previousTarget.getVictimID())) {
                Human bestHuman = (Human) worldInfo.getEntity(bestTarget.getVictimID());
                Human previousHuman = (Human) worldInfo.getEntity(previousTarget.getVictimID());

                double bestHumanCost = pathPlanning.setFrom(agentInfo.getPosition()).setDestination(bestHuman.getPosition()).calc().getDistance();
                double previousHumanCost = pathPlanning.setFrom(agentInfo.getPosition()).setDestination(previousHuman.getPosition()).calc().getDistance();
                if (previousHumanCost < bestHumanCost) {
                    bestTarget = previousTarget;
                }
            }
        }

        previousTarget = bestTarget;

        if (bestTarget != null) {
            return bestTarget.getVictimID();
        } else {
            return null;
        }

    }

    private void calculateMapCenters(int clusterIndex, Collection<StandardEntity> elements) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = 0, maxY = 0;
        for (StandardEntity entity : elements) {
            Pair<Integer, Integer> location = worldInfo.getLocation(entity);
            minX = Math.min(location.first(), minX);
            minY = Math.min(location.second(), minY);

            maxX = Math.max(location.first(), maxX);
            maxY = Math.max(location.second(), maxY);
        }

        Pair<Integer, Integer> center = new Pair<>((minX + maxX) / 2, (minY + maxY) / 2);
        clusterCenterMap.put(clusterIndex, center);

    }

    private void refreshTargetsMap(Set<StandardEntity> victims, Map<EntityID, AmbulanceTarget> targetsMap) {
        List<EntityID> toRemoves = new ArrayList<EntityID>();
        for (EntityID targetID : targetsMap.keySet()) {
            if (!victims.contains(worldInfo.getEntity(targetID))) {
                toRemoves.add(targetID);
            }
        }
        for (EntityID entityID : toRemoves) {
            targetsMap.remove(entityID);
        }
    }

    private AmbulanceTarget findBestVictim(Map<EntityID, AmbulanceTarget> targetsMap, Collection<StandardEntity> elements) {
        AmbulanceTarget bestTarget = null;
        if (targetsMap != null && !targetsMap.isEmpty()) {

            double minValue = Double.MAX_VALUE;
            for (AmbulanceTarget target : targetsMap.values()) {
                if (target.getDistanceToMe() <= rescueRange || elements.contains(worldInfo.getEntity(target.getPositionID())) /*|| Util.distance(myBasePartition.getPolygon(), worldInfo.getEntity(target.getPositionID()).getLocation(worldInfo.getRawWorld())) < rescueRange*/) {
//                    if (target.getCost() < minValue && target.getCost() < threshold) {
                    if (target.getDistanceToMe() < minValue) {
//                        minValue = target.getCost();
                        minValue = target.getDistanceToMe();
                        bestTarget = target;
                    }
                }
            }

        }

        return bestTarget;
    }

    private void calculateVictimsCostValue() {

        if (targetsMap != null && !targetsMap.isEmpty()) {
            double cost = 0;
            double rw = .9;//refuge Weight
            double pdw = 2.7;//my Partition Distance Weight
            double mdw = 1.5;//My Distance Weight
            double vsw = 1.5; //Victim Situation Weight

            for (AmbulanceTarget target : targetsMap.values()) {
                cost = 1 + rw * target.getDistanceToRefuge() / MEAN_VELOCITY_OF_MOVING
                        + pdw * target.getDistanceToPartition() / MEAN_VELOCITY_OF_MOVING
                        + mdw * target.getDistanceToMe() / MEAN_VELOCITY_OF_MOVING
                        - vsw * target.getVictimSituation();
                target.setCost(cost);
            }

        }

    }

    /**
     * Calculates different parameters for a target such as distance of the target to nearest refuge, distance of target to
     * this agent partitions, distance of this agent to the target and situations of the target based on its BRD and DMG
     *
     * @param victims    victims to calculate their parameters
     * @param targetsMap map of previously found targets
     */
    private void calculateDecisionParameters(Set<StandardEntity> victims, Map<EntityID, AmbulanceTarget> targetsMap) {

        AmbulanceTarget target;
        Human human;
        if (victims != null && !victims.isEmpty()) {
            for (StandardEntity victim : victims) {
                target = targetsMap.get(victim.getID());
                human = (Human) worldInfo.getEntity(victim.getID());
                if (target == null) {
                    //creating a new AmbulanceTarget object
                    target = new AmbulanceTarget(victim.getID());

                    //set target position
                    target.setPositionID(human.getPosition());

                    //euclidean distance from this victim to the nearest refuge
                    target.setDistanceToRefuge(worldInfo.getDistance(human.getPosition(), findNearestRefuge(human.getPosition())));

                    target.setDistanceToPartition(Util.distance(victim.getLocation(worldInfo.getRawWorld()), clusterCenterMap.get(this.clusterIndex)));
                }
                //euclidean distance from this victim to the me
                target.setDistanceToMe(computingDistance(human));

                target.setVictimSituation(calculateVictimProfitability(human));

                targetsMap.put(victim.getID(), target);
            }
        }

    }


    private EntityID findNearestRefuge(EntityID positionId) {

        Collection<StandardEntity> refuges = worldInfo.getEntitiesOfType(StandardEntityURN.REFUGE);
        EntityID nearestID = null;
        int nearestDistance = Integer.MAX_VALUE;
        int tempDistance;
        if (positionId != null && refuges != null && !refuges.isEmpty()) {

            for (StandardEntity refugeEntity : refuges) {
                tempDistance = worldInfo.getDistance(refugeEntity.getID(), positionId);
                if (tempDistance < nearestDistance) {
                    nearestDistance = tempDistance;
                    nearestID = refugeEntity.getID();
                }
            }

        }

        return nearestID;
    }

    /**
     * This method computes distance based on euclidean distance.
     * <br>
     * <br>
     * <b>Note:</b> Based on human importance, the distance may be changed to lower value
     *
     * @param human the human to calculate its distance to me.
     * @return euclidean distance from human to me
     */
    private int computingDistance(Human human) {

        double coefficient = 1;
        if (human instanceof AmbulanceTeam) {
            coefficient = 0.90;
        } else if (human instanceof PoliceForce) {
            coefficient = 0.95;
        } else if (human instanceof FireBrigade) {
            coefficient = 0.97;
        } else {//human is instance of Civilian
            coefficient = 1;
        }


        return (int) (coefficient * Util.distance(human.getPosition(worldInfo.getRawWorld()).getLocation(worldInfo.getRawWorld()), worldInfo.getLocation(agentInfo.me())));
    }

    /**
     * calculates victim profitability
     *
     * @param human target human (kossher)
     * @return
     */
    private double calculateVictimProfitability(Human human) {

        int ttd = (int) Math.ceil(human.getHP() / (double) human.getDamage() * 0.8); //a pessimistic time to death

        double profitability = 100 / (double) ((human.getBuriedness() * ttd) + 1);

        return profitability;
    }
}
