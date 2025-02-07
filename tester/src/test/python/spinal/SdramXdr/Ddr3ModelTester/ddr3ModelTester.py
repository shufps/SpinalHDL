import random
import cocotb
from cocotb.triggers import Timer

from Verilog_VCD.Verilog_VCD import parse_vcd
from spinal.SdramXdr.common.VcdLib import *


@cocotb.test()
def test1(dut):
    random.seed(0)
    from cocotblib.misc import cocotbXHack
    cocotbXHack()

    forks = []
    def map(component, net, apply, delay = 0):
        forks.append(cocotb.fork(stim(wave, component, net, apply, delay)))


    wave = parse_vcd("/home/miaou/pro/riscv/SaxonSoc.git/simWorkspace/SdrXdrCtrlPlusRtlPhy/test.vcd")
    phy = "TOP.SdrXdrCtrlPlusRtlPhy"
    top = "TOP"

    yield Timer(0)
    phaseCount = getLastValue(wave, top, "phaseCount")
    dataRate = 2
    phaseDelay = 0
    clockPeriod = getClockPeriod(wave, top, "clk")

    cocotb.fork(genClock(dut.ck, dut.ck_n, clockPeriod//phaseCount))

    map(top, "ADDR", lambda v : dut.addr <= v)
    map(top, "BA", lambda v : dut.ba <= v)
    map(top, "CASn", lambda v : dut.cas_n <= v)
    map(top, "CKE", lambda v : dut.cke <= v)
    map(top, "CSn", lambda v : dut.cs_n <= v)
    map(top, "RASn", lambda v : dut.ras_n <= v)
    map(top, "WEn", lambda v : dut.we_n <= v)
    map(top, "RESETn", lambda v : dut.rst_n <= v)
    map(top, "ODT", lambda v : dut.odt <= v)

    cocotb.fork(stimPulse(wave, top, "writeEnable", lambda v : cocotb.fork(genDqs(dut.dqs, dut.dqs_n, 1+v/clockPeriod*phaseCount*dataRate//2, clockPeriod//(phaseCount*dataRate)*(phaseCount*dataRate-1), clockPeriod//phaseCount))))

    for fork in forks:
        yield fork
