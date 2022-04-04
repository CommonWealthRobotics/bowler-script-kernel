import eu.mihosoft.vrl.v3d.CSG

CSG wedge = new Wedge(60,25,40).toCSG()
wedge.addExportFormat("svg")
[
[
wedge,
new Isosceles(60,25,40).toCSG()
],
DeviceManager.getSpecificDevice( "MediumKat",{
			return ScriptingEngine.gitScriptRun(	"https://github.com/OperationSmallKat/SmallKat_V2.git", 
											"loadRobot.groovy", 
											[ "https://github.com/OperationSmallKat/greycat.git",
											  "MediumKat.xml",
											  "GameController_22"]
			  )
		})
		, null,
		[null,wedge.movey(-75)]
		]