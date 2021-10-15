package mrl_2021.comm;

import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.component.communication.ChannelSubscriber;
import rescuecore2.standard.entities.StandardEntityURN;

import java.util.Arrays;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class MChannelSubscriber extends ChannelSubscriber
{
    @Override
    public void subscribe(
        AgentInfo ai, WorldInfo wi, ScenarioInfo si,
        MessageManager mm)
    {
        final int channels = si.getCommsChannelsCount()-1;
        final int max = computeMaxChannels(ai, si);

        int[] ret = new int[max];
        for (int i=0; i<max; ++i) ret[i] = assignChannel(ai, i, channels);

        mm.subscribeToChannels(ret);
    }

    final static StandardEntityURN[] AGENT_URNS =
    {
        FIRE_BRIGADE, FIRE_STATION,
        POLICE_FORCE, POLICE_OFFICE,
        AMBULANCE_TEAM, AMBULANCE_CENTRE,
    };

    private static int computeMaxChannels(AgentInfo ai, ScenarioInfo si)
    {
        final StandardEntityURN type = ai.me().getStandardURN();
        final int np = si.getCommsChannelsMaxPlatoon();
        final int no = si.getCommsChannelsMaxOffice();
        return computeMaxChannels(type, np, no);
    }

    public static int computeMaxChannels(
        StandardEntityURN type, int np, int no)
    {
        final int i = Arrays.asList(AGENT_URNS).indexOf(type);

        final boolean platoon = i%2 == 0;
        return platoon ? np : no;
    }

    private static int assignChannel(AgentInfo ai, int index, int num)
    {
        final StandardEntityURN type = ai.me().getStandardURN();
        return assignChannel(type, index, num);
    }

    public static int assignChannel(StandardEntityURN type, int index, int num)
    {
        final int n = AGENT_URNS.length;
        final int i = Arrays.asList(AGENT_URNS).indexOf(type);

        final int group = n/2;
        final int order = i/2;
        final int ideal = group*index + order;

        return num>0 ? ideal%num + 1 : 0;
    }
}
