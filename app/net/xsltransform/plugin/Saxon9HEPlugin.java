package net.xsltransform.plugin;

import play.Play;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;

public class Saxon9HEPlugin implements TransformerPlugin {

    private static final String VERSION = "saxon-9.8.0.7-he";

    private static final String TRANSFORMER_FACTORY_CLASS_NAME = "net.sf.saxon.TransformerFactoryImpl";

//    private static final ClassLoader saxon9HEClassLoader = new JarClassLoader(new InputStream[]{
//            Play.application().resourceAsStream("public/plugins/"+VERSION+"/"+VERSION+".jar")
//    }, Saxon9HEPlugin.class.getClassLoader());
    
    private static final ClassLoader saxon9HEClassLoader = new URLClassLoader(new URL[]{
            Play.application().resource("public/plugins/"+VERSION+"/"+VERSION+".jar")
    }, Saxon9HEPlugin.class.getClassLoader());
    
    private static TransformerFactory getSecureTransformerFactory() throws IllegalArgumentException, TransformerConfigurationException
    {
    	try {
			TransformerFactory transformerFactory = TransformerFactory.newInstance(TRANSFORMER_FACTORY_CLASS_NAME, saxon9HEClassLoader);
			
			transformerFactory.setAttribute("http://saxon.sf.net/feature/allow-external-functions", "off");
			
			Class EnvironmentVariableResolverInterface = saxon9HEClassLoader.loadClass("net.sf.saxon.lib.EnvironmentVariableResolver");
			
			Object dummyResolver =  Proxy.newProxyInstance(EnvironmentVariableResolverInterface.getClassLoader(),
					new Class<?>[] { EnvironmentVariableResolverInterface },
					new InvocationHandler() {
						@Override
						public Object invoke(Object o, Method method, Object[] os) throws Throwable {
							if (method.getName().equals("getEnvironmentVariable"))
							{
								return null;
							}
							else if (method.getName().equals("getAvailableEnvironmentVariables"))
							{
								return new HashSet<String>(0);
							}
							else throw new RuntimeException("Unknown method " + method.getName());                 
						}
					});
			
			transformerFactory.setAttribute("http://saxon.sf.net/feature/environmentVariableResolver", dummyResolver);
			
			Class CollectionFinder = saxon9HEClassLoader.loadClass("net.sf.saxon.lib.CollectionFinder");
			
			Object dummyFinder = Proxy.newProxyInstance(
				CollectionFinder.getClassLoader(),
				new Class<?>[] { CollectionFinder },
					new InvocationHandler() {
						@Override
						public Object invoke(Object o, Method method, Object[] os) throws Throwable {
							if (method.getName().equals("findCollection"))
							{
								return null;
							}
							else throw new RuntimeException("Unknown method " + method.getName());                 
						}
					});
			
			transformerFactory.setAttribute("http://saxon.sf.net/feature/collection-finder", dummyFinder);
						
			return transformerFactory;
		}
		catch (ClassNotFoundException e)
		{
			throw new TransformerConfigurationException("Unable to set up Saxon Transformer:" + e.getMessage());
		}
    }
    

    @Override
    public Transformer newTransformer() throws TransformerConfigurationException {
        return getSecureTransformerFactory().newTransformer();
    }

    @Override
    public Transformer newTransformer(Source source, ByteArrayOutputStream errors) throws TransformerConfigurationException {

        TransformerFactory transformerFactory = getSecureTransformerFactory();
        

        try {
            Class FeatureKeysClass = saxon9HEClassLoader.loadClass("net.sf.saxon.lib.FeatureKeys");
            Class ConfigurationClass = saxon9HEClassLoader.loadClass("net.sf.saxon.Configuration");
            Class StandardErrorListenerClass = saxon9HEClassLoader.loadClass("net.sf.saxon.lib.StandardErrorListener");
            Class TransformerFactoryClass = saxon9HEClassLoader.loadClass(TRANSFORMER_FACTORY_CLASS_NAME);
            Class ProcessorClass = saxon9HEClassLoader.loadClass("net.sf.saxon.s9api.Processor");
            Class StandardLoggerClass =  saxon9HEClassLoader.loadClass("net.sf.saxon.lib.StandardLogger");
            
            Object proc = TransformerFactoryClass.getMethod("getProcessor").invoke(transformerFactory);

            Object conf = ProcessorClass.getMethod("getUnderlyingConfiguration").invoke(proc);  //transformerFactory.getAttribute((String)FeatureKeysClass.getField("CONFIGURATION").get(null));
            Object errorListener = ConfigurationClass.getMethod("getErrorListener").invoke(conf);
            
            Object saxonLogger = StandardErrorListenerClass.getMethod("getLogger").invoke(errorListener);
            
            StandardLoggerClass.getMethod("setPrintStream", PrintStream.class).invoke(
            	saxonLogger, 
            	new PrintStream(errors)
            );

            return transformerFactory.newTransformer(source);

        } catch (ClassNotFoundException e) {
            throw new TransformerConfigurationException("net.sf.saxon.TransformerFactoryImpl not found");
        } catch (NoSuchMethodException e) {
            throw new TransformerConfigurationException("net.sf.saxon.TransformerFactoryImpl no such method for error listener");
        } catch (IllegalAccessException e) {
            throw new TransformerConfigurationException("net.sf.saxon.TransformerFactoryImpl not accessible");
        } catch (InvocationTargetException e) {
            throw new TransformerConfigurationException("net.sf.saxon.TransformerFactoryImpl could invoke method for error listener");
        } 
        
    }
}
