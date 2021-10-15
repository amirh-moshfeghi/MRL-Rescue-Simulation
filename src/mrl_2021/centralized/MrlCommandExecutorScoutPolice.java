package mrl_2021.centralized;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.centralized.CommandScout;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.centralized.CommandExecutor;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;

public class MrlCommandExecutorScoutPolice extends CommandExecutor<CommandScout> {

    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_SCOUT = 1;

    private PathPlanning pathPlanning;

    private ExtAction actionExtClear;

    private int commandType;
    private Collection<EntityID> scoutTargets;
    private EntityID commanderID;

    public MrlCommandExecutorScoutPolice(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
    }

    @Override
    public CommandExecutor<CommandScout> setCommand(CommandScout command) {
        return this;
    }

    public CommandExecutor precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }

    public CommandExecutor resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        return this;
    }

    public CommandExecutor preparate() {
        super.preparate();
        return this;
    }

    public CommandExecutor updateInfo(MessageManager messageManager){
        super.updateInfo(messageManager);
        return this;
    }

    @Override
    public CommandExecutor calc() {
        return this;
    }
}
