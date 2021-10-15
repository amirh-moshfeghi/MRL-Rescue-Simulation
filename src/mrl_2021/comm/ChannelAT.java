package mrl_2021.comm;

import adf.agent.communication.standard.bundle.StandardMessage;
import adf.agent.communication.standard.bundle.centralized.CommandAmbulance;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessageCivilian;
import adf.agent.info.AgentInfo;
import rescuecore2.standard.entities.StandardEntityURN;

import java.util.Arrays;

import static rescuecore2.standard.entities.StandardEntityURN.AMBULANCE_TEAM;

public class ChannelAT extends AbstractChannel
{
    private static final Class[] WHITE_LIST =
    {
        MessageAmbulanceTeam.class,
        MessageCivilian.class,
        CommandAmbulance.class,
    };

    private static final Class[] UNION_LIST =
    {
        //MessageReport.class,
        //CommandScout.class
    };

    private AgentInfo ai;

    public ChannelAT(AgentInfo ai, int[] numbers)
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

        final boolean inWhitelist =
            Arrays.asList(WHITE_LIST).contains(clazz);
        final boolean inUnionlist =
            Arrays.asList(UNION_LIST).contains(clazz);

        return inWhitelist || inUnionlist && urn == AMBULANCE_TEAM;
    }
}
