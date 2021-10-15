package mrl_2021.centralized;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.centralized.CommandAmbulance;
import adf.agent.communication.standard.bundle.centralized.MessageReport;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.centralized.CommandExecutor;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.Set;

import static rescuecore2.standard.entities.StandardEntityURN.CIVILIAN;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class MCommandExecutorAmbulance extends CommandExecutor<CommandAmbulance>
{
    private static final int ACTION_UNKNOWN = -1;

    private EntityID target;
    private int type;
    private EntityID commander;

    private ExtAction extaction;
    private PathPlanning pathPlanning;

    public MCommandExecutorAmbulance(
        AgentInfo ai, WorldInfo wi, ScenarioInfo si,
        ModuleManager mm, DevelopData dd)
    {
        super(ai, wi, si, mm, dd);
        this.type = ACTION_UNKNOWN;

        this.extaction = mm.getExtAction(
            "CommandExecutorAmbulance.ActionTransport",
            "adf.sample.extaction.ActionTransport");

        this.pathPlanning = mm.getModule(
            "CommandExecutorAmbulance.PathPlanning",
            "adf.sample.module.algorithm.SamplePathPlanning");
    }

    @Override
    public CommandExecutor setCommand(CommandAmbulance command)
    {
        final EntityID me = this.agentInfo.getID();
        if (!command.isToIDDefined()) return this;
        if (!me.equals(command.getToID())) return this;

        this.target = command.getTargetID();
        this.type = command.getAction();
        this.commander = command.getSenderID();
        return this;
    }

    @Override
    public CommandExecutor precompute(PrecomputeData pd)
    {
        super.precompute(pd);
        if (this.getCountPrecompute() >= 2) return this;

        this.extaction.precompute(pd);
        this.pathPlanning.precompute(pd);
        return this;
    }

    @Override
    public CommandExecutor resume(PrecomputeData pd)
    {
        super.resume(pd);
        if (this.getCountResume() >= 2) return this;

        this.extaction.resume(pd);
        this.pathPlanning.resume(pd);
        return this;
    }

    @Override
    public CommandExecutor preparate()
    {
        super.preparate();
        if (this.getCountPreparate() >= 2) return this;

        this.extaction.preparate();
        this.pathPlanning.preparate();
        return this;
    }

    @Override
    public CommandExecutor updateInfo(MessageManager mm)
    {
        super.updateInfo(mm);
        if (this.getCountUpdateInfo() >= 2) return this;

        this.extaction.updateInfo(mm);
        this.pathPlanning.updateInfo(mm);

        if (this.target != null && this.isCommandCompleted())
        {
            mm.addMessage(new MessageReport(true, true, true, this.target));
            this.target = null;
            this.type = ACTION_UNKNOWN;
            this.commander = null;
        }

        return this;
    }

    @Override
    public CommandExecutor calc()
    {
        this.result = null;
        if (this.target == null) return this;
        if (this.type == ACTION_UNKNOWN) return this;

        this.extaction.setTarget(this.target);
        this.extaction.calc();
        this.result = this.extaction.getAction();
        return this;
    }

    private boolean isCommandCompleted()
    {
        if (this.needIdle()) return false;

        //this.extaction.setTarget(this.target);
        //this.extaction.calc();
        //final Action action = this.extaction.getAction();
        //return this.isEmptyAction(action);

        final Set<EntityID> changes =
            this.worldInfo.getChanged().getChangedEntities();
        if (!changes.contains(this.target)) return false;

        final StandardEntity entity = this.worldInfo.getEntity(this.target);
        if (!Human.class.isInstance(entity)) return true;
        final Human human = (Human)entity;

        final boolean isBuried =
            human.isBuriednessDefined() && human.getBuriedness() > 0;
        if (human.getStandardURN() != CIVILIAN) return !isBuried;

        final boolean isDamaged =
            human.isDamageDefined() && human.getDamage() > 0;
        final StandardEntity position =
            this.worldInfo.getEntity(human.getPosition());
        final boolean isOnRefuge = position.getStandardURN() == REFUGE;

        return !isBuried && !isDamaged || isOnRefuge;
    }

    private boolean isEmptyAction(Action action)
    {
        if (action == null) return true;
        if (action instanceof ActionRest) return true;
        if (action instanceof ActionMove)
        {
            final ActionMove move = (ActionMove)action;
            final int ax = (int)this.agentInfo.getX();
            final int ay = (int)this.agentInfo.getY();
            final int mx = move.getPosX();
            final int my = move.getPosY();
            return ax == mx && ay == my;
        }
        return false;
    }

    private boolean needIdle()
    {
        final int time = this.agentInfo.getTime();
        final int ignored = this.scenarioInfo.getKernelAgentsIgnoreuntil();
        return time < ignored;
    }
}
