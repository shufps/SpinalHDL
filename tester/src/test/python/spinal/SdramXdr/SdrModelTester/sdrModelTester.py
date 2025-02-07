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
    top = "TOP"

    yield Timer(0)
    phaseCount = getLastValue(wave, top, "phaseCount")
    clockPeriod = getClockPeriod(wave, top, "clk")

    cocotb.fork(genClock(dut.Clk, None, clockPeriod//phaseCount))

    map(top, "ADDR", lambda v : dut.Addr <= v)
    map(top, "BA", lambda v : dut.Ba <= v)
    map(top, "CASn", lambda v : dut.Cas_n <= v)
    map(top, "CKE", lambda v : dut.Cke <= v)
    map(top, "CSn", lambda v : dut.Cs_n <= v)
    map(top, "RASn", lambda v : dut.Ras_n <= v)
    map(top, "WEn", lambda v : dut.We_n <= v)


    for fork in forks:
        yield fork
