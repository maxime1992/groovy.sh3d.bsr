/*
 * This Groovy source file was generated by the Gradle 'init' task.
 */
package nl.tkvw.sh3d.bsr

import com.eteks.sweethome3d.io.HomeFileRecorder
import com.eteks.sweethome3d.j3d.PhotoRenderer
import com.eteks.sweethome3d.model.Camera
import com.eteks.sweethome3d.model.Home
import com.eteks.sweethome3d.model.HomeEnvironment
import com.eteks.sweethome3d.model.HomeLight
import com.eteks.sweethome3d.model.HomeObject
import nl.tkvw.sh3d.bsr.config.RenderInstructions
import org.sunflow.system.UI
import org.sunflow.system.ui.ConsoleInterface

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors

class App {

    static void main(String[] args) {
        def cli = new groovy.cli.picocli.CliBuilder(name: "Batch Scenario Renderer")
        cli.c(type: String,longOpt: "config",required:true,argName:'configFile', 'Yaml configuration file with instruction to use')

        def options = cli.parse(args);
        new App().run(RenderInstructions.fromYamlConfig(options.config));
    }

    void run(RenderInstructions renderInstructions){
        ensureOutputFolder(renderInstructions);
        assert renderInstructions.floorplan : "Input is a required field in the render instructions configuration file"
        assert new File(renderInstructions.floorplan).exists() : "Floorplan file (${renderInstructions.floorplan}) does not exists "

        Home home;
        HomeEnvironment environment;
        try{
            home = new HomeFileRecorder().readHome(renderInstructions.floorplan);
            environment = home.environment;
        }
        catch(Exception e){
            throw new Exception("Could not load floorplan from ${renderInstructions.floorplan}, please check if there is a version mismatch",e)
        }
        List<HomeObject> homeObjects = home.getHomeObjects();
        def homeLights = homeObjects.findAll({it instanceof HomeLight}).collect({it as HomeLight})

        assert !renderInstructions.scenarios?.isEmpty(): "There are no scenarios defined"
        renderInstructions.scenarios.each{scenario ->
            def scenarioFolder = new File(renderInstructions.output,scenario.name);
            if(!scenarioFolder.exists()) scenarioFolder.mkdirs();

            scenario.pictures.each{picture ->
                def format = picture.format?:scenario.format?:renderInstructions.format?: 'png';
                def exportImage = new File(scenarioFolder,"${picture.name}.${format}")

                def cameraName = picture.camera?: scenario.camera?: renderInstructions.camera
                if(cameraName){
                    def camera = home.storedCameras.find{cameraName.equalsIgnoreCase(it.name)}
                    if(camera){
                        home.camera = camera
                    }
                }
                def timestamp = picture.timestamp?: scenario.timestamp?: renderInstructions.timestamp;
                if(timestamp){
                    def timeZone = renderInstructions.timezone? ZoneId.of(renderInstructions.timezone):ZoneId.systemDefault();
                    def timestampFormat = renderInstructions.timestampFormat?: 'dd-MM-yyyy HH:mm:ss';
                    def localDateTime = LocalDateTime.parse(timestamp,DateTimeFormatter.ofPattern(timestampFormat))
                    def timestampInMillisFromEpoch = localDateTime.atZone(timeZone).toInstant().toEpochMilli();
                    home.camera.time = timestampInMillisFromEpoch;
                }
                def width = picture.width?:scenario.width?:renderInstructions.width;
                def height = picture.height?:scenario.height?:renderInstructions.height;
                if(width && environment.photoWidth != width || height && height!= environment.photoHeight){
                    if(height==null){
                        height = (int) Math.ceil((width/ (double) environment.photoWidth)*environment.photoHeight);
                    }
                    if(width == null){
                        width = (int) Math.ceil((height/(double)environment.photoHeight)*environment.photoWidth);
                    }
                    environment.photoWidth=width
                    environment.photoHeight= height
                }

                // Reset environment lights
                homeLights.each{it.power = 0.0}

                if(!picture.lights?.empty){
                    picture.lights.each{light ->
                        homeLights.find {light.name.equalsIgnoreCase(it.name)}?.power = (light.power/100 as float)
                    }
                }

                try{
                    println "Render picture ${picture.name} of scenario ${scenario.name} to ${exportImage.absolutePath}"
                    def renderer = new PhotoRenderer(home,
                            environment.photoQuality == 3
                                    ? PhotoRenderer.Quality.HIGH
                                    : PhotoRenderer.Quality.LOW);
                    UI.set(new ConsoleInterface());
                    BufferedImage image = new BufferedImage(
                            environment.getPhotoWidth(), environment.getPhotoHeight(),
                            BufferedImage.TYPE_INT_ARGB);
                    renderer.render(image, home.camera, null);
                    ImageIO.write(image, format,exportImage);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static void ensureOutputFolder(RenderInstructions renderInstructions) {
        assert null != renderInstructions.output : "Output folder is required in the instruction configuration"
        def f = new File(renderInstructions.output)
        if(!f.exists()){
            assert f.mkdirs(): "Output folder does not exists and could not be created"
        }
    }
}