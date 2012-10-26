package org.apache.mahout.clustering.elkan;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.mahout.clustering.Cluster;
import org.apache.mahout.clustering.classify.ClusterClassifier;
import org.apache.mahout.clustering.iterator.ClusterIterator;
import org.apache.mahout.clustering.iterator.ClusterWritable;
import org.apache.mahout.clustering.iterator.ClusteringPolicy;
import org.apache.mahout.clustering.kmeans.KMeansConfigKeys;
import org.apache.mahout.common.ClassUtils;
import org.apache.mahout.common.distance.DistanceMeasure;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.SequentialAccessSparseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.VectorWritable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElkanClusterDistanceStepJob {

	public static int run(Path input, Path output, Configuration conf) {
		try {
			Job job = new Job(conf, "Calculate centroids distances job.");

			job.setMapperClass(ElkanClusterDistanceMapper.class);
			job.setMapOutputKeyClass(IntWritable.class);
			job.setMapOutputValueClass(VectorWritable.class);

			job.setReducerClass(Reducer.class);
			job.setOutputKeyClass(IntWritable.class);
			job.setOutputValueClass(VectorWritable.class);

			job.setOutputFormatClass(SequenceFileOutputFormat.class);
			FileOutputFormat.setOutputPath(job, output);

			job.setInputFormatClass(SequenceFileInputFormat.class);
			FileInputFormat.setInputPaths(job, input);

			job.setJarByClass(ElkanClusterDistanceStepJob.class);

			job.waitForCompletion(true);
		} catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace(System.err);
			return 1;
		}

		return 0;
	}

	public static class ElkanClusterDistanceMapper extends
			Mapper<IntWritable, ClusterWritable, IntWritable, VectorWritable> {

		private static final Logger log = LoggerFactory
				.getLogger(ElkanClusterDistanceMapper.class);

		private ClusterClassifier classifier;

		private ClusteringPolicy policy;

		private DistanceMeasure measure;

		@Override
		protected void setup(Context context) throws IOException,
				InterruptedException {
			Configuration conf = context.getConfiguration();
			String measureClass = conf
					.get(KMeansConfigKeys.DISTANCE_MEASURE_KEY);
			measure = ClassUtils.instantiateAs(measureClass,
					DistanceMeasure.class);

			String priorClustersPath = conf.get(ClusterIterator.PRIOR_PATH_KEY);
			classifier = new ClusterClassifier();
			classifier.readFromSeqFiles(conf, new Path(priorClustersPath));
			policy = classifier.getPolicy();
			policy.update(classifier);

			super.setup(context);
		}

		@Override
		protected void map(IntWritable key, ClusterWritable value,
				Context context) throws IOException, InterruptedException {
			Vector clusterOrigin = value.getValue().getCenter();

			List<Cluster> models = classifier.getModels();
			Vector distances = new DenseVector(models.size());

			int j = 0;
			for (Cluster model : models) {
				distances.setQuick(j, measure.distance(clusterOrigin,model.getCenter()));
				j++;
			}

			context.write(key, new VectorWritable(distances));
		}
	}

}
