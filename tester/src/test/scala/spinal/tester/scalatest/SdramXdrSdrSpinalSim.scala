package spinal.tester.scalatest

import spinal.core._
import spinal.core.sim.SpinalSimConfig
import spinal.lib.bus.bmb._
import spinal.lib.memory.sdram.sdr.{MT41K128M16JT, MT48LC16M16A2, SdramInterface}
import spinal.lib.memory.sdram.xdr.{BmbPortParameter, CoreParameter, CtrlParameter, CtrlWithPhy, CtrlWithoutPhy, PhyLayout, SdramTiming, SoftConfig, mt48lc16m16a2_model}
import spinal.lib._
import spinal.lib.bus.amba3.apb.Apb3
import spinal.lib.bus.amba3.apb.sim.Apb3Driver
import spinal.lib.bus.bmb.sim.{BmbMemoryMultiPort, BmbMemoryMultiPortTester}
import spinal.lib.eda.bench.Rtl
import spinal.lib.memory.sdram.SdramLayout
import spinal.lib.memory.sdram.sdr.sim.SdramModel
import spinal.lib.memory.sdram.xdr.phy.{RtlPhy, RtlPhyInterface, SdrInferedPhy, XilinxS7Phy}
import spinal.lib.sim.Phase

import scala.util.Random

class SdrXdrCtrlPlusPhy(cp : CtrlParameter, pl : PhyLayout) extends Component{
  val ctrl = new CtrlWithoutPhy(cp, pl)

  val bmb = Vec(cp.ports.map(p => slave(Bmb(p.bmb))))
  val apb = slave(Apb3(12, 32))
  bmb <> ctrl.io.bmb
  apb <> ctrl.io.apb
}

class SdrXdrCtrlPlusRtlPhy(val cp : CtrlParameter,val pl : PhyLayout) extends SdrXdrCtrlPlusPhy(cp, pl){
  val phy = RtlPhy(pl)
  phy.io.ctrl <> ctrl.io.phy

  val phyWrite = master(RtlPhyInterface(phy.pl))
  phyWrite <> phy.io.write

  val beatCounter = out(Reg(UInt(60 bits)) init(0))
  beatCounter := beatCounter + ctrl.io.phy.readEnable.asUInt + ctrl.io.phy.writeEnable.asUInt


  val clockCounter = out(Reg(UInt(60 bits)) init(0))
  clockCounter := clockCounter + 1

  val sel = in UInt(log2Up(pl.phaseCount) bits)
  val phaseCount = out(U(pl.phaseCount))
  val writeEnable = out(CombInit(ctrl.io.phy.writeEnable))
  val ADDR = out(CombInit(ctrl.io.phy.ADDR))
  val BA = out(CombInit(ctrl.io.phy.BA))
  val CASn = out(ctrl.io.phy.phases.map(_.CASn).read(sel))
  val CKE = out(ctrl.io.phy.phases.map(_.CKE).read(sel))
  val CSn = out(ctrl.io.phy.phases.map(_.CSn).read(sel))
  val RASn = out(ctrl.io.phy.phases.map(_.RASn).read(sel))
  val WEn = out(ctrl.io.phy.phases.map(_.WEn).read(sel))
  val RESETn = pl.sdram.generation.RESETn generate out(ctrl.io.phy.phases.map(_.RESETn).read(sel))
  val ODT = pl.sdram.generation.ODT generate out(ctrl.io.phy.phases.map(_.ODT).read(sel))

//  val phyCtrl = out(CombInit(phy.io.ctrl))
}

object SpinalSdrTesterHelpers{
  def CSn = 1 << 1
  def RASn = 1 << 2
  def CASn = 1 << 3
  def WEn = 1 << 4

  def PRE = CASn
  def REF = WEn
  def MOD = 0

  def SDRAM_CONFIG = 0x000
  def SDRAM_PHASE = 0x004
  def SDRAM_WRITE_LATENCY = 0x008
  def SDRAM_READ_LATENCY = 0x00C

  def SDRAM_AUTO_REFRESH = 1
  def SDRAM_NO_ACTIVE = 2

  def SDRAM_SOFT_PUSH = 0x100
  def SDRAM_SOFT_CMD = 0x104
  def SDRAM_SOFT_ADDR = 0x108
  def SDRAM_SOFT_BA = 0x10C
  def SDRAM_SOFT_CLOCKING = 0x110

  def SDRAM_FAW = 0x030
  def SDRAM_ODT = 0x034


  def SDRAM_RESETN = 1
  def SDRAM_CKE = 2


  def SDRAM_CSN = (1 << 1)
  def SDRAM_RASN  = (1 << 2)
  def SDRAM_CASN  = (1 << 3)
  def SDRAM_WEN  = (1 << 4)


  def SDRAM_PRE = (SDRAM_CASN)
  def SDRAM_REF = (SDRAM_WEN)
  def SDRAM_MOD = (0)
  def SDRAM_ZQCL = (SDRAM_RASN | SDRAM_CASN)



  def ctrlParameter(pl : PhyLayout, cp : CoreParameter) = CtrlParameter(
    core = cp,
    ports = Seq(
      //TODO
//      BmbPortParameter(
//        bmb = BmbParameter(
//          addressWidth = pl.sdram.byteAddressWidth,
//          dataWidth = pl.beatWidth,
//          lengthWidth = log2Up(8 * pl.bytePerBurst),
//          sourceWidth = 3,
//          contextWidth = 8
//        ),
//        clockDomain = ClockDomain.current,
//        cmdBufferSize = 64,
//        dataBufferSize = 64 * pl.beatCount,
//        rspBufferSize = 64 * pl.beatCount
//      )
      BmbPortParameter(
        bmb = BmbParameter(
          addressWidth = pl.sdram.byteAddressWidth,
          dataWidth = pl.beatWidth,
          lengthWidth = log2Up(16*pl.bytePerBurst),
          sourceWidth = 3,
          contextWidth = 8
        ),
        clockDomain = ClockDomain.current,
        cmdBufferSize = 16,
        dataBufferSize = 16*pl.beatCount,
        rspBufferSize = 16*pl.beatCount
      ),

      BmbPortParameter(
        bmb = BmbParameter(
          addressWidth = pl.sdram.byteAddressWidth,
          dataWidth = pl.beatWidth,
          lengthWidth = log2Up(32*pl.bytePerBurst),
          sourceWidth = 3,
          contextWidth = 8
        ),
        clockDomain = ClockDomain.current,
        cmdBufferSize = 32,
        dataBufferSize = 32*pl.beatCount,
        rspBufferSize = 32*pl.beatCount
      ),

      BmbPortParameter(
        bmb = BmbParameter(
          addressWidth = pl.sdram.byteAddressWidth,
          dataWidth = pl.beatWidth,
          lengthWidth = log2Up(8*pl.bytePerBurst),
          sourceWidth = 3,
          contextWidth = 8
        ),
        clockDomain = ClockDomain.current,
        cmdBufferSize = 4,
        dataBufferSize = 1*pl.beatCount,
        rspBufferSize = 4*pl.beatCount
      ),

      BmbPortParameter(
        bmb = BmbParameter(
          addressWidth = pl.sdram.byteAddressWidth,
          dataWidth = pl.beatWidth,
          lengthWidth = log2Up(8*pl.bytePerBurst),
          sourceWidth = 3,
          contextWidth = 8
        ),
        clockDomain = ClockDomain.current,
        cmdBufferSize = 1,
        dataBufferSize = 8*pl.beatCount,
        rspBufferSize = 8*pl.beatCount
      )
    )
  )

//  def sdrInit(dut : SdrXdrCtrlPlusRtlPhy, timing : SdramTiming, cas : Int, phyClkRatio : Int): Unit ={
//    import spinal.core.sim._
//    Phase.setup {
//      val apb = Apb3Driver(dut.apb, dut.clockDomain)
//      //apb.verbose = true
//
//      val soft = SoftConfig(timing, dut.clockDomain.frequency.getValue, dut.ctrl.cpa, phyClkRatio)
//      apb.write(0x10, soft.REF)
//
//      apb.write(0x20, (soft.RRD << 24) | (soft.RFC << 16) | (soft.RP << 8)  | (soft.RAS << 0))
//      apb.write(0x24,                                                         (soft.RCD << 0))
//      apb.write(0x28, (soft.WTP << 24)  | (soft.WTR << 16) | (soft.RTP << 8) |    (cas+2 << 0))
//
//      sleep(100000)
//
//      def command(cmd : Int,  bank : Int, address : Int): Unit ={
//        apb.write(0x10C, bank)
//        apb.write(0x108, address)
//        apb.write(0x104, cmd)
//        apb.write(0x100, 0)
//        dut.clockDomain.waitSampling(10)
//      }
//
//      command(PRE, 0, 0x400)
//      command(REF, 0, 0)
//      command(REF, 0, 0)
//      command(MOD, 0, 0x000 | (cas << 4))
//      apb.write(0x04, 1)
//
//      dut.clockDomain.waitSampling(10000)
//    }
//  }

  def configInit(apb : Apb3Driver,
                 timing : SdramTiming,
                 rl : Int,
                 wl : Int,
                 bl : Int,
                 phyClkRatio : Int,
                 sdramPeriod : Int,
                 cRTW_IDLE : Int,
                 cRRD_MIN : Int): Unit ={
    val readToDataCycle = (rl+phyClkRatio-1)/phyClkRatio;
    val readPhase = readToDataCycle*phyClkRatio-rl;
    val writeToDataCycle = (wl+phyClkRatio-1)/phyClkRatio;
    val writePhase = writeToDataCycle*phyClkRatio-wl;
    val activePhase = 0
    val prechargePhase = 0


    def t2c(startPhase : Int, nextPhase : Int, duration : Int) = (startPhase + (duration + sdramPeriod - 1)/sdramPeriod - nextPhase + phyClkRatio - 1) / phyClkRatio
    def sat(v : Int) = v.max(0)
    val ctrlPeriod = sdramPeriod*phyClkRatio
    val cREF = t2c(0, 0, timing.REF)
    val cRAS = t2c(activePhase     , prechargePhase                 , timing.RAS)
    val cRP  = t2c(prechargePhase  , activePhase                    , timing.RP)
    val cRFC = t2c(activePhase     , activePhase                    , timing.RFC)
    val cRRD = t2c(activePhase     , activePhase                    , Math.max(timing.RRD, cRRD_MIN*sdramPeriod))
    val cRCD = t2c(activePhase     , Math.min(writePhase, readPhase), timing.RCD)
    val cRTW = t2c(readPhase       , writePhase                     , (rl+bl+cRTW_IDLE-wl)*sdramPeriod)
    val cRTP = t2c(readPhase       , prechargePhase                 , Math.max(timing.RTP, bl*sdramPeriod))
    val cWTR = t2c(writePhase      , readPhase                      , Math.max(timing.WTR, bl*sdramPeriod) + (wl+bl)*sdramPeriod)
    val cWTP = t2c(writePhase      , prechargePhase                 , timing.WTP + (wl+bl)*sdramPeriod)
    val cFAW = t2c(activePhase     , activePhase                    , timing.FAW)

    apb.write( SDRAM_PHASE, (prechargePhase << 24) | (activePhase << 16) | (readPhase << 8) | (writePhase << 0));
    apb.write( SDRAM_WRITE_LATENCY, 0)
    apb.write( SDRAM_READ_LATENCY, 0)
    apb.write( SDRAM_CONFIG, SDRAM_NO_ACTIVE);

    apb.write(0x10, cREF-1)
    apb.write(0x20, (sat(cRRD-2) << 24) | (sat(cRFC-2) << 16) | (sat(cRP-2) << 8)   |   (sat(cRAS-2) << 0))
    apb.write(0x24,                                                                     (sat(cRCD-2) << 0))
    apb.write(0x28, (sat(cWTP-2) << 24)  | (sat(cWTR-2) << 16) | (sat(cRTP-2) << 8)   | (sat(cRTW-2) << 0))
    apb.write(SDRAM_FAW, sat(cFAW-1));

    var ODTend = (1 << (writePhase + 6)%phyClkRatio)-1
    if(ODTend == 0) ODTend = (1 << phyClkRatio)-1
    val ODT = (writePhase+6+phyClkRatio-1)/phyClkRatio-1
    apb.write(SDRAM_ODT, (ODT << 0) | (ODTend << 8));
  }

  def ddr3Init(dut : SdrXdrCtrlPlusRtlPhy,
               timing : SdramTiming,
               rl : Int,
               wl : Int,
               bl : Int,
               phyClkRatio : Int,
               sdramPeriod : Int): Unit ={
    import spinal.core.sim._
    Phase.setup {
      val apb = Apb3Driver(dut.apb, dut.clockDomain)

      configInit(
        apb = apb ,
        timing = timing ,
        rl = rl ,
        wl = wl ,
        bl = bl ,
        phyClkRatio = phyClkRatio ,
        sdramPeriod = sdramPeriod,
        cRTW_IDLE = 2,
        cRRD_MIN = 4
      )

      val wrToMr = List(1,2,3,4,-1,5,-1,6,-1,7,-1,0);
      val rlToMr = List(2,4,6,8,10,12,14,1,3,5);
      def io_udelay(us : Int) = {}//sleep(us*1000000)
      def command(cmd : Int,  bank : Int, address : Int): Unit ={
        apb.write(0x10C, bank)
        apb.write(0x108, address)
        apb.write(0x104, cmd)
        apb.write(0x100, 0)
        dut.clockDomain.waitSampling(10)
      }

      apb.write(SDRAM_SOFT_CLOCKING, 0);
      io_udelay(200);
      apb.write(SDRAM_SOFT_CLOCKING, SDRAM_RESETN);
      io_udelay(500);
      apb.write(SDRAM_SOFT_CLOCKING, SDRAM_RESETN | SDRAM_CKE);

      command(SDRAM_MOD, 2, 0x200 | ((wl - 5) << 3));
      command(SDRAM_MOD, 3, 0);
      command(SDRAM_MOD, 1, 0x44);
      command(SDRAM_MOD, 0, (wrToMr(wl - 5) << 9) | 0x100 | ((rlToMr(rl-5) & 1) << 2) | ((rlToMr(rl-5) & 0xE) << 3)); //DDL reset
      io_udelay(100);
      command(SDRAM_ZQCL, 0, 0x400);
      io_udelay(100);

      apb.write(SDRAM_CONFIG, SDRAM_AUTO_REFRESH);

      dut.clockDomain.waitSampling(10000)
    }
  }

  def sdrInit( dut : SdrXdrCtrlPlusRtlPhy,
               timing : SdramTiming,
               rl : Int,
               wl : Int,
               bl : Int,
               phyClkRatio : Int,
               sdramPeriod : Int): Unit ={
    import spinal.core.sim._
    Phase.setup {
      val apb = Apb3Driver(dut.apb, dut.clockDomain)

      configInit(
        apb = apb ,
        timing = timing ,
        rl = rl ,
        wl = wl ,
        bl = bl ,
        phyClkRatio = phyClkRatio ,
        sdramPeriod = sdramPeriod,
        cRTW_IDLE = 2,
        cRRD_MIN = 4
      )

      val wrToMr = List(1,2,3,4,-1,5,-1,6,-1,7,-1,0);
      val rlToMr = List(2,4,6,8,10,12,14,1,3,5);
      def io_udelay(us : Int) = {}//sleep(us*1000000)
      def command(cmd : Int,  bank : Int, address : Int): Unit ={
        apb.write(0x10C, bank)
        apb.write(0x108, address)
        apb.write(0x104, cmd)
        apb.write(0x100, 0)
        dut.clockDomain.waitSampling(10)
      }


      apb.write(SDRAM_SOFT_CLOCKING, 0); io_udelay(100);
      apb.write(SDRAM_SOFT_CLOCKING, SDRAM_CKE); io_udelay(100);
      command(SDRAM_PRE,0,0x400); io_udelay(1);
      command(SDRAM_REF,0,0x000); io_udelay(1);
      command(SDRAM_REF,0,0x000); io_udelay(1);
      command(SDRAM_MOD,0,rl << 4); io_udelay(1);
      apb.write(SDRAM_CONFIG, SDRAM_AUTO_REFRESH);

      dut.clockDomain.waitSampling(10000)
    }
  }


  def setup(dut : SdrXdrCtrlPlusRtlPhy, noStall : Boolean = false, sdramPeriod : Int): Unit ={
    import spinal.core.sim._
    def sl = dut.pl.sdram
    val addressTop = 1 << (2 + sl.bankWidth + sl.columnWidth + log2Up(sl.bytePerWord))
    val bytePerBeat = dut.phy.pl.bytePerBeat

    val tester = new BmbMemoryMultiPortTester(
      ports = dut.bmb.map(port =>
        BmbMemoryMultiPort(
          bmb = port,
          cd = dut.clockDomain
        )
      ),
      forkClocks = false
    ){
      override def addressGen(bmb: Bmb): Int = Random.nextInt(addressTop)
      override def transactionCountTarget: Int = 20
    }

    Phase.setup {
      for(beatId <- 0 until addressTop/bytePerBeat){
        var data = BigInt(0)
        for(byteId <- 0 until bytePerBeat){
          data = data | (BigInt(tester.memory.getByte(beatId*bytePerBeat + byteId).toInt & 0xFF) << (byteId*8))
        }
        dut.phyWrite.clk #= false
        dut.phyWrite.cmd.valid #= true
        dut.phyWrite.cmd.address #= beatId
        dut.phyWrite.cmd.data #= data
        sleep(0)
        dut.phyWrite.clk #= true
        sleep(0)
      }
    }

    var beatCount = 0l
    var clockCount = 0l
    Phase.stimulus{
      beatCount = -dut.beatCounter.toLong
      clockCount = -dut.clockCounter.toLong
    }

    Phase.flush{
      beatCount += dut.beatCounter.toLong
      clockCount += dut.clockCounter.toLong
      println(s"${1.0*beatCount/clockCount} beatRate ($beatCount/$clockCount)")
    }

    if(dut.phy.pl.phaseCount != 1) fork {
      var counter = 0
      dut.clockDomain.waitSampling()
      stuff()
      def stuff(): Unit = {
        dut.sel #= counter
        counter += 1
        if(counter == dut.phy.pl.phaseCount) counter = 0
        delayed(sdramPeriod)(stuff)
      }
    }
  }

  def complied(rl : Int,
               wl : Int,
               bl : Int,
               sdramPeriod : Int,
               pl : PhyLayout,
               timing : SdramTiming) ={

    import spinal.core.sim._
    val phyClkRatio = pl.phaseCount
    val simConfig = SimConfig
    simConfig.withWave
//    simConfig.withWave(1)
    simConfig.addSimulatorFlag("-Wno-MULTIDRIVEN")
    simConfig.withConfig(SpinalConfig(defaultClockDomainFrequency = FixedFrequency(1e12/(sdramPeriod*phyClkRatio) Hz)))
    simConfig.compile({
      val cp = SpinalSdrTesterHelpers.ctrlParameter(
        pl = pl,
        cp = CoreParameter(
          portTockenMin = 4,
          portTockenMax = 16,
          timingWidth = 5,
          refWidth = 16,
          writeLatencies = List((wl+phyClkRatio-1)/phyClkRatio - pl.cmdToDqDelayDelta),
          readLatencies = List((rl+phyClkRatio-1)/phyClkRatio)
        )
      )
      new SdrXdrCtrlPlusRtlPhy(cp, pl)
    })
  }
}


object SdramXdrDdr3SpinalSim extends App{
  import spinal.core.sim._

  val rl = 5
  val wl = 5
  val bl = 4
  val sdramPeriod = 3300
  val sl = MT41K128M16JT.layout
  val pl = XilinxS7Phy.phyLayout(sl, clkRatio = 2)
  val timing = SdramTiming(
    REF =  7800000,
    RAS =    35000,
    RP  =    13750,
    RFC =   160000,
    RRD =     7500,
    RCD =    13750,
    RTP =     7500,
    WTR =     7500,
    WTP =    15000,
    FAW =    40000
  )

  SpinalSdrTesterHelpers.complied(
    rl = rl ,
    wl = wl ,
    bl = bl ,
    sdramPeriod = sdramPeriod ,
    pl = pl ,
    timing = timing
  ).doSimUntilVoid("test", 42) { dut =>
    dut.clockDomain.forkStimulus(sdramPeriod*pl.phaseCount)
    SpinalSdrTesterHelpers.setup(dut, noStall = false, sdramPeriod = sdramPeriod)
    SpinalSdrTesterHelpers.ddr3Init(
      dut = dut,
      timing = timing,
      rl = rl,
      wl = wl,
      bl = bl,
      phyClkRatio = pl.phaseCount,
      sdramPeriod = sdramPeriod
    )
  }
}


object SdramXdrSdrSpinalSim extends App{
  import spinal.core.sim._

  val rl = 2
  val wl = 0
  val bl = 1
  val sdramPeriod = 6250
  val sl = MT48LC16M16A2.layout
  val pl = SdrInferedPhy.phyLayout(sl)
  val timing = SdramTiming(
    REF =  7812500,
    RAS =    42000,
    RP  =    18000,
    RFC =    60000,
    RRD =    12000,
    RCD =    18000,
    RTP =        0,
    WTR =        0,
    WTP =     6000,
    FAW =        0
  )

//  bootRefreshCount =   8,
//  tPOW             = 100 us,
//  tREF             =  64 ms,
//  tRC              =  60 ns,
//  tRFC             =  66 ns,
//  tRAS             =  37 ns,
//  tRP              =  15 ns,
//  tRCD             =  15 ns,
//  cMRD             =   2,
//  tWR              =  7.5 ns,
//  cWR              =  1

  SpinalSdrTesterHelpers.complied(
    rl = rl ,
    wl = wl ,
    bl = bl ,
    sdramPeriod = sdramPeriod ,
    pl = pl ,
    timing = timing
  ).doSimUntilVoid("test", 42) { dut =>
    dut.clockDomain.forkStimulus(sdramPeriod*pl.phaseCount)
    SpinalSdrTesterHelpers.setup(dut, noStall = false, sdramPeriod = sdramPeriod)
    SpinalSdrTesterHelpers.sdrInit(
      dut = dut,
      timing = timing,
      rl = rl,
      wl = wl,
      bl = bl,
      phyClkRatio = pl.phaseCount,
      sdramPeriod = sdramPeriod
    )
  }
}






import spinal.core._
import spinal.lib.eda.bench._

object SdramSdrSyntBench extends App{
  val sl = MT48LC16M16A2.layout.copy(bankWidth = 3)
  val cp = CtrlParameter(
    core = CoreParameter(
      portTockenMin = 4,
      portTockenMax = 8,
      timingWidth = 4,
      refWidth = 16,
      writeLatencies = List(0),
      readLatencies = List(2)
    ),
    ports = Seq(
      BmbPortParameter(
        bmb = BmbParameter(
          addressWidth = sl.byteAddressWidth,
          dataWidth = 16,
          lengthWidth = 3,
          sourceWidth = 0,
          contextWidth = 0
        ),
        clockDomain = ClockDomain.current,
        cmdBufferSize = 8,
        dataBufferSize = 8,
        rspBufferSize = 16
      ),

      BmbPortParameter(
        bmb = BmbParameter(
          addressWidth = sl.byteAddressWidth,
          dataWidth = 16,
          lengthWidth = 4,
          sourceWidth = 0,
          contextWidth = 0
        ),
        clockDomain = ClockDomain.current,
        cmdBufferSize = 8,
        dataBufferSize = 8,
        rspBufferSize = 16
      )/*,

      BmbPortParameter(
        bmb = BmbParameter(
          addressWidth = sl.byteAddressWidth,
          dataWidth = 16,
          lengthWidth = 5,
          sourceWidth = 0,
          contextWidth = 0
        ),
        clockDomain = ClockDomain.current,
        cmdBufferSize = 8,
        rspBufferSize = 2
      )*//*,

      BmbPortParameter(
        bmb = BmbParameter(
          addressWidth = sl.byteAddressWidth,
          dataWidth = 16,
          lengthWidth = 5,
          sourceWidth = 0,
          contextWidth = 0
        ),
        clockDomain = ClockDomain.current,
        cmdBufferSize = 8,
        rspBufferSize = 2
      )*/
    )
  )


  val ports4 = new Rtl {
    override def getName(): String = "Port4"
    override def getRtlPath(): String = "Port4.v"
    SpinalVerilog({
      val c = new CtrlWithoutPhy(cp, SdrInferedPhy.phyLayout(sl)).setDefinitionName(getRtlPath().split("\\.").head)
      c
    })
  }


  val rtls = List(ports4)

  val targets = XilinxStdTargets(
    vivadoArtix7Path = "/media/miaou/HD/linux/Xilinx/Vivado/2018.3/bin"
  ) ++ AlteraStdTargets(
    quartusCycloneIVPath = "/media/miaou/HD/linux/intelFPGA_lite/18.1/quartus/bin",
    quartusCycloneVPath  = "/media/miaou/HD/linux/intelFPGA_lite/18.1/quartus/bin"
  )

  Bench(rtls, targets, "/media/miaou/HD/linux/tmp")




}