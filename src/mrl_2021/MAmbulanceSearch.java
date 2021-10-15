package mrl_2021;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.Search;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.EntityID;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static rescuecore2.standard.entities.StandardEntityURN.*;

// @ DEBUG {{

// }}

public class MAmbulanceSearch extends Search
{
    private EntityID result;

    private Set<EntityID> cluster = new HashSet<>();
    private Map<EntityID, Set<EntityID>> potentials = new HashMap<>();
    private Set<EntityID> reached = new HashSet<>();

    private Set<EntityID> delayed = new HashSet<>();
    private double penalty = 0.0;

    private Clustering clusterer;
    private Clustering failedMove;
    private Clustering stuckedHumans;

    // @ DEBUG {{{
    // private VDClient vdclient = VDClient.getInstance();
    // }}}

    public MAmbulanceSearch(
        AgentInfo ai, WorldInfo wi, ScenarioInfo si,
        ModuleManager mm, DevelopData dd)
    {
        super(ai, wi, si, mm, dd);

        String key = "SampleSearch.Clustering";
        switch (ai.me().getStandardURN())
        {
            case FIRE_BRIGADE:
                key += ".Fire";
                break;
            case AMBULANCE_TEAM:
                key += ".Ambulance";
                break;
            case POLICE_FORCE:
                key += ".Police";
                break;
            default:
                System.err.println("Unexpected URN");
        }

        this.clusterer = mm.getModule(
            key, "adf.sample.module.algorithm.SampleKMeans");
        this.registerModule(this.clusterer);

        this.failedMove =
            mm.getModule("AIT_2019.module.algorithm.FailedMove");
        this.registerModule(this.failedMove);

        this.stuckedHumans =
            mm.getModule("AIT_2019.module.algorithm.StuckedHumans");
        this.registerModule(this.stuckedHumans);

        // @ DEBUG {{{
        // this.vdclient.init("localhost", 1099);
        // }}}
    }

    @Override
    public EntityID getTarget()
    {
        return this.result;
    }

    @Override
    public Search calc()
    {
        if (this.needToExpandCluster()) this.expandCluster();

        Set<EntityID> candidates = new HashSet<>(this.cluster);
        candidates.removeAll(this.reached);
        this.result = candidates.stream().max(this.comparator()).orElse(null);
        return this;
    }

    private void initCluster()
    {
        this.clusterer.calc();

        final EntityID me = this.agentInfo.getID();
        final int index = this.clusterer.getClusterIndex(me);
        final Collection<EntityID> buildings =
            this.gatherBuildings(this.clusterer.getClusterEntityIDs(index));

        this.cluster.addAll(buildings);
        this.penalty = this.computeClusterDiagonal();
    }

    private void expandCluster()
    {
        final EntityID me = this.agentInfo.getID();

        final int n = this.clusterer.getClusterNumber();
        final int index = this.clusterer.getClusterIndex(me);

        final int size = this.cluster.size();
        for (int i=1; i<n && size==this.cluster.size(); ++i)
        {
            final Collection<EntityID> buildings =
                this.gatherBuildings(
                    this.clusterer.getClusterEntityIDs(index+i*n));
            this.cluster.addAll(buildings);
        }
    }

    private boolean needToExpandCluster()
    {
        return this.reached.size() >= this.cluster.size()*0.9;
    }

    private Set<EntityID> gatherBuildings(Collection<EntityID> collection)
    {
        final Stream<EntityID> ret =
            collection
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Building.class::isInstance)
                .map(StandardEntity::getID);

        return ret.collect(toSet());
    }

    private Comparator<EntityID> comparator()
    {
        final EntityID me = this.agentInfo.getID();
        final Set<EntityID> empty = Collections.emptySet();

        final Comparator<EntityID> comparator1 = comparing(
            i -> this.potentials.getOrDefault(i, empty).size());
        final Comparator<EntityID> comparator2 = comparing(
            i -> this.worldInfo.getDistance(i, me)
                + (this.delayed.contains(i) ? this.penalty : 0.0));

        return comparator1.thenComparing(comparator2.reversed());
    }

    @Override
    public Search updateInfo(MessageManager mm)
    {
        super.updateInfo(mm);
        if (this.getCountUpdateInfo() > 1) return this;

        if (this.cluster.isEmpty()) this.initCluster();

        final Human human = (Human)this.agentInfo.me();
        if (this.cannotReach() && human.getBuriedness() == 0)
            this.delayed.add(this.result);

        this.reflectVoiceToPotentials();
        this.ignoreReachedBuildings();
        this.ignoreUntaskableBuildings();
        this.ignoreBuildingsOnFire();
        this.ignoreDiscoveredCivilians();

        return this;
    }

    private void reflectVoiceToPotentials()
    {
        final Set<EntityID> candidates = this.gatherBuildingsInVoiceRange();
        final Set<EntityID> civilians = this.gatherHelpedCivilians();

        candidates.forEach(i ->
        {
            this.potentials
                .computeIfAbsent(i, k -> new HashSet<>())
                .addAll(civilians);
        });
    }

    private Set<EntityID> gatherBuildingsInVoiceRange()
    {
        final int range = this.scenarioInfo.getRawConfig()
            .getIntValue("comms.channels.0.range");

        final EntityID me = this.agentInfo.getID();
        final Stream<EntityID> ret =
            this.worldInfo.getObjectsInRange(me, range)
                .stream()
                .filter(Building.class::isInstance)
                .map(StandardEntity::getID);

        return ret.collect(toSet());
    }

    private Set<EntityID> gatherHelpedCivilians()
    {
        final Set<EntityID> agents =
            new HashSet<>(this.worldInfo.getEntityIDsOfType(
                FIRE_BRIGADE, FIRE_STATION,
                AMBULANCE_TEAM, AMBULANCE_CENTRE,
                POLICE_FORCE, POLICE_OFFICE));

        final Stream<EntityID> ret =
            this.agentInfo.getHeard()
                .stream()
                .filter(AKSpeak.class::isInstance)
                .map(AKSpeak.class::cast)
                .filter(s -> s.getChannel() == 0)
                .map(AKSpeak::getAgentID)
                .filter(i -> !agents.contains(i));

        return ret.collect(toSet());
    }

    private void ignoreReachedBuildings()
    {
        final EntityID position = this.agentInfo.getPosition();
        if (this.worldInfo.getEntity(position) instanceof Building)
            this.reached.add(position);

        this.worldInfo.getChanged().getChangedEntities()
            .stream()
            .map(this.worldInfo::getEntity)
            .filter(Building.class::isInstance)
            .map(Building.class::cast)
            .filter(this::isReached)
            .map(Building::getID)
            .forEach(this.reached::add);

        this.potentials.keySet().removeAll(this.reached);
    }

    private boolean isReached(Building building)
    {
        final int max = this.scenarioInfo.getPerceptionLosMaxDistance();
        final Line2D line = new Line2D(
            this.getPoint(),
            new Point2D(building.getX(), building.getY()));

        if (line.getDirection().getLength() >= max*0.8) return false;
        for (Edge edge : building.getEdges())
        {
            if (!edge.isPassable()) continue;

            final Point2D intersection =
                GeometryTools2D.getSegmentIntersectionPoint(
                    line, edge.getLine());
            if (intersection != null) return true;
        }

        return false;
    }

    private void ignoreUntaskableBuildings()
    {
        this.worldInfo.getChanged().getChangedEntities()
            .stream()
            .map(this.worldInfo::getEntity)
            .filter(Building.class::isInstance)
            .map(Building.class::cast)
            .filter(Building::isBrokennessDefined)
            .filter(b -> b.getBrokenness() == 0)
            .map(Building::getID)
            .forEach(this.reached::add);

        this.potentials.keySet().removeAll(this.reached);
    }

    private void ignoreBuildingsOnFire()
    {
        this.worldInfo.getChanged().getChangedEntities()
            .stream()
            .map(this.worldInfo::getEntity)
            .filter(Building.class::isInstance)
            .map(Building.class::cast)
            .filter(Building::isOnFire)
            .map(Building::getID)
            .forEach(this.reached::add);

        this.potentials.keySet().removeAll(this.reached);
    }

    private Point2D getPoint()
    {
        final double x = this.agentInfo.getX();
        final double y = this.agentInfo.getY();
        return new Point2D(x, y);
    }

    private void ignoreDiscoveredCivilians()
    {
        final Collection<EntityID> changed =
            this.worldInfo.getChanged().getChangedEntities();

        final Set<EntityID> ignored =
            changed
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Civilian.class::isInstance)
                .map(StandardEntity::getID)
                .collect(toSet());

        this.potentials.values().forEach(vs -> vs.removeAll(ignored));
    }

    private boolean cannotReach()
    {
        final EntityID me = this.agentInfo.getID();
        final StandardEntityURN urn = this.agentInfo.me().getStandardURN();

        if (this.result == null) return false;
        if (urn == POLICE_FORCE) return false;

        this.failedMove.calc();
        this.stuckedHumans.calc();

        final boolean failed = this.failedMove.getClusterIndex(me) >= 0;
        final boolean stucked = this.stuckedHumans.getClusterIndex(me) >= 0;
        return stucked || failed;
    }

    private double computeClusterDiagonal()
    {
        final List<Building> buildings =
            this.cluster
                .stream()
                .map(this.worldInfo::getEntity)
                .map(Building.class::cast)
                .collect(toList());

        final int minX =
            buildings.stream().mapToInt(Area::getX).min().orElse(0);
        final int minY =
            buildings.stream().mapToInt(Area::getY).min().orElse(0);
        final int maxX =
            buildings.stream().mapToInt(Area::getX).max().orElse(0);
        final int maxY =
            buildings.stream().mapToInt(Area::getY).max().orElse(0);

        return Math.hypot(maxX-minX, maxY-minY);
    }
}
