package mrl_2021.comm;

import adf.agent.communication.standard.bundle.StandardMessage;
import adf.agent.communication.standard.bundle.StandardMessagePriority;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.info.AgentInfo;
import rescuecore2.standard.entities.StandardEntityURN;

import java.util.Arrays;

import static adf.agent.communication.standard.bundle.StandardMessagePriority.HIGH;
import static rescuecore2.standard.entities.StandardEntityURN.POLICE_FORCE;

public class ChannelPF extends AbstractChannel
{
    private static final Class[] WHITE_LIST =
    {
        MessagePoliceForce.class,
        MessageFireBrigade.class,
        MessageAmbulanceTeam.class,
        //MessageRoad.class,
        CommandPolice.class,
    };

    private static final Class[] UNION_LIST =
    {
        //MessageReport.class,
        //CommandScout.class
    };

    private AgentInfo ai;

    public ChannelPF(AgentInfo ai, int[] numbers)
    {
        super(numbers, true);
        this.ai = ai;
    }

    @Override
    protected boolean applyFilter(StandardMessage message)
    {
        if (!super.applyFilter(message)) return false;

        final StandardEntityURN urn = this.ai.me().getStandardURN();
        final Class clazz = message.getClass();
        final StandardMessagePriority priority = message.getSendingPriority();
        if (clazz == CommandPolice.class && priority != HIGH) return false;

        final boolean inWhitelist =
            Arrays.asList(WHITE_LIST).contains(clazz);
        final boolean inUnionlist =
            Arrays.asList(UNION_LIST).contains(clazz);

        return inWhitelist || inUnionlist && urn == POLICE_FORCE;
    }
}
