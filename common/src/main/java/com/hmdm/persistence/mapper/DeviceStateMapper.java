package com.hmdm.persistence.mapper;

import com.hmdm.persistence.domain.DeviceState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis mapper for the latest agent-reported device snapshot ({@link DeviceState}).
 * One row per device number; {@link #upsert} replaces the prior snapshot.
 */
public interface DeviceStateMapper {

    @Insert({"INSERT INTO device_state " +
            "(deviceNumber, battery, charging, locked, kioskActive, androidRelease, lastBootAt, updatedAt, agentVersion, powerMode, telemetry, configurationCapabilities) " +
            "VALUES (#{deviceNumber}, #{battery}, #{charging}, #{locked}, #{kioskActive}, " +
            "#{androidRelease}, #{lastBootAt}, #{updatedAt}, #{agentVersion}, #{powerMode}, #{telemetry}, NULL) " +
            "ON CONFLICT (deviceNumber) DO UPDATE SET " +
            "battery = EXCLUDED.battery, charging = EXCLUDED.charging, locked = EXCLUDED.locked, " +
            "kioskActive = EXCLUDED.kioskActive, androidRelease = EXCLUDED.androidRelease, " +
            "lastBootAt = EXCLUDED.lastBootAt, updatedAt = EXCLUDED.updatedAt, " +
            "agentVersion = EXCLUDED.agentVersion, powerMode = EXCLUDED.powerMode, telemetry = EXCLUDED.telemetry, configurationCapabilities = NULL"})
    void upsert(DeviceState state);

    /** One statement commits legacy advertisement and the complete qualified
     * snapshot together. The snapshot carries only THIS check-in's bounded
     * control tokens; a missing capability frame stays unavailable. */
    @Insert({"WITH capabilities AS (UPDATE devices SET agentCapabilities = COALESCE(#{capabilitiesJson}, agentCapabilities) " +
            "WHERE number = #{state.deviceNumber} RETURNING number) " +
            "INSERT INTO device_state " +
            "(deviceNumber,battery,charging,locked,kioskActive,androidRelease,lastBootAt,updatedAt,agentVersion,powerMode,telemetry,configurationCapabilities) " +
            "SELECT #{state.deviceNumber},#{state.battery},#{state.charging},#{state.locked},#{state.kioskActive}," +
            "#{state.androidRelease},#{state.lastBootAt},#{state.updatedAt},#{state.agentVersion},#{state.powerMode},#{state.telemetry},#{state.configurationCapabilities} FROM capabilities " +
            "ON CONFLICT(deviceNumber) DO UPDATE SET battery=EXCLUDED.battery,charging=EXCLUDED.charging,locked=EXCLUDED.locked," +
            "kioskActive=EXCLUDED.kioskActive,androidRelease=EXCLUDED.androidRelease,lastBootAt=EXCLUDED.lastBootAt,updatedAt=EXCLUDED.updatedAt," +
            "agentVersion=EXCLUDED.agentVersion,powerMode=EXCLUDED.powerMode,telemetry=EXCLUDED.telemetry,configurationCapabilities=EXCLUDED.configurationCapabilities"})
    void upsertWithCapabilities(@Param("state") DeviceState state, @Param("capabilitiesJson") String capabilitiesJson);

    /** A capability/results-only check-in cannot retain an older qualified
     * snapshot. It may still refresh the legacy command-gating advertisement. */
    @Update({"WITH capabilities AS (UPDATE devices SET agentCapabilities=COALESCE(#{capabilitiesJson},agentCapabilities) " +
            "WHERE number=#{deviceNumber} RETURNING number) " +
            "UPDATE device_state SET configurationCapabilities=NULL WHERE deviceNumber IN (SELECT number FROM capabilities)"})
    void updateCapabilitiesWithoutState(@Param("deviceNumber") String deviceNumber, @Param("capabilitiesJson") String capabilitiesJson);

    @Select({"SELECT deviceNumber, battery, charging, locked, kioskActive, androidRelease, lastBootAt, updatedAt, agentVersion, powerMode, telemetry, configurationCapabilities " +
            "FROM device_state WHERE deviceNumber = #{deviceNumber}"})
    DeviceState findByDeviceNumber(@Param("deviceNumber") String deviceNumber);
}
