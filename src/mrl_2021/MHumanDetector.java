package mrl_2021;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.HumanDetector;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static java.util.Comparator.comparing;
import static rescuecore2.standard.entities.StandardEntityURN.*;

public class MHumanDetector extends HumanDetector
{
    private PathPlanning pathPlanner;
    private Clustering clusterer;

    private EntityID result = null;
    private Set<EntityID> cluster = new HashSet<>();
    private Set<EntityID> ignored = new HashSet<>();

    private static final double AGENT_CAN_MOVE = 7000.0;

    public MHumanDetector(
        AgentInfo ai, WorldInfo wi, ScenarioInfo si,
        ModuleManager mm, DevelopData dd)
    {
        super(ai, wi, si, mm, dd);

        StandardEntityURN urn = ai.me().getStandardURN();
        if (urn == AMBULANCE_TEAM)
        {
            this.pathPlanner = mm.getModule(
                "ActionTransport.PathPlanning",
                "adf.sample.module.algorithm.SamplePathPlanning");
        }
        else
        if (urn == FIRE_BRIGADE)
        {
            this.pathPlanner = mm.getModule(
                "ActionFireRescue.PathPlanning",
                "adf.sample.module.algorithm.SamplePathPlanning");
        }
        this.registerModule(this.pathPlanner);

        this.clusterer = mm.getModule(
            "SampleHumanDetector.Clustering",
            "adf.sample.module.algorithm.SampleKMeans");
        this.registerModule(this.clusterer);
    }

    @Override
    public EntityID getTarget()
    {
        return this.result;
    }

    @Override
    public HumanDetector updateInfo(MessageManager mm)
    {
        super.updateInfo(mm);
        if (this.getCountUpdateInfo() > 1) return this;

        if (this.cluster.isEmpty()) this.initCluster();
        this.updateIgnoredHumans();

        return this;
    }

    @Override
    public HumanDetector calc()
    {
        this.result = null;
        final Human onboard = this.agentInfo.someoneOnBoard();
        if (onboard != null)
        {
            this.result = onboard.getID();
            return this;
        }

        Set<EntityID> agents = new HashSet<>(
            this.worldInfo.getEntityIDsOfType(
                FIRE_BRIGADE, AMBULANCE_TEAM, POLICE_FORCE));
        final EntityID me = this.agentInfo.getID();
        agents.remove(me);

        final Set<EntityID> civilians = new HashSet<>(
            this.worldInfo.getEntityIDsOfType(CIVILIAN));

        if (this.agentInfo.me().getStandardURN() == AMBULANCE_TEAM)
        {
            this.result = civilians
                .stream()
                .filter(this::canLoad)
                .min(comparing(this::cost)).orElse(null);
            if (this.result != null) return this;
        }

        this.result = agents
            .stream()
            .filter(i -> this.cluster.contains(this.worldInfo.getPosition(i)))
            .filter(this::needToRescue)
            .min(comparing(this::cost)).orElse(null);
        if (this.result != null) return this;

        this.result = civilians
            .stream()
            .filter(i -> this.cluster.contains(this.worldInfo.getPosition(i)))
            .filter(this::needToRescue)
            .min(comparing(this::cost)).orElse(null);
        if (this.result != null) return this;

        this.result = agents
            .stream()
            .filter(this::needToRescue)
            .min(comparing(this::cost)).orElse(null);
        if (this.result != null) return this;

        this.result = civilians
            .stream()
            .filter(this::needToRescue)
            .min(comparing(this::cost)).orElse(null);
        return this;
    }

    private void initCluster()
    {
        this.clusterer.calc();

        final EntityID me = this.agentInfo.getID();
        final int index = this.clusterer.getClusterIndex(me);
        final Collection<EntityID> ids =
            this.clusterer.getClusterEntityIDs(index);
        this.cluster.addAll(ids);
    }

    private double cost(EntityID id)
    {
        final EntityID me = this.agentInfo.getID();
        final double time2reach =
            this.worldInfo.getDistance(me, id) / AGENT_CAN_MOVE;

        final Human human = (Human)this.worldInfo.getEntity(id);
        final int hp = human.getHP();
        final int damage = human.getDamage();
        final int buriedness = human.getBuriedness();

        return (time2reach + buriedness) * damage - hp;
    }

    private boolean needToRescue(EntityID id)
    {
        if (this.ignored.contains(id)) return false;

        final StandardEntity position = this.worldInfo.getPosition(id);
        if (position.getStandardURN() == REFUGE) return false;

        final Human human = (Human)this.worldInfo.getEntity(id);
        final int buriedness = human.isBuriednessDefined() ?
            human.getBuriedness() : 0;
        return buriedness > 0 && this.canSave(id);
    }

    private boolean canSave(EntityID id)
    {
        final EntityID me = this.agentInfo.getID();
        final double time2reach =
            this.worldInfo.getDistance(me, id) / AGENT_CAN_MOVE;

        final Human human = (Human)this.worldInfo.getEntity(id);
        final int hp = human.getHP();
        final int damage = human.getDamage();
        final int buriedness = human.getBuriedness();

        return (time2reach + buriedness) * damage < hp;
    }

    private boolean canLoad(EntityID id)
    {
        if (this.ignored.contains(id)) return false;

        final Human human = (Human)this.worldInfo.getEntity(id);
        if (human.getStandardURN() != CIVILIAN) return false;

        final StandardEntity position = this.worldInfo.getPosition(id);
        if (position.getStandardURN() == REFUGE) return false;
        if (!this.agentInfo.getPosition().equals(position.getID()))
            return false;

        final int damage = human.getDamage();
        final int buriedness = human.getBuriedness();
        return damage > 0 && buriedness == 0;
    }

    private void updateIgnoredHumans()
    {
        final Set<EntityID> changes =
            this.worldInfo.getChanged().getChangedEntities();

        final Set<EntityID> humans = new HashSet<>(
            this.worldInfo.getEntityIDsOfType(
                FIRE_BRIGADE, AMBULANCE_TEAM, POLICE_FORCE, CIVILIAN));
        for (EntityID human : humans)
        {
            final Human entity = (Human)this.worldInfo.getEntity(human);
            final EntityID position = entity.getPosition();
            if (changes.contains(position)) this.ignored.add(human);
        }

        this.ignored.removeAll(changes);
    }
}
