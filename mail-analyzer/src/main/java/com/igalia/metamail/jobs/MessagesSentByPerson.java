package com.igalia.metamail.jobs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import com.igalia.metamail.utils.JobRunner;
import com.igalia.metamail.utils.MailRecord;

/**
 * 
 * Emails sent by person (size, msgid:sender)
 * 
 * @author Diego Pino García <dpino@igalia.com>
 * 
 * 
 */
public class MessagesSentByPerson {

	private static final String jobName = "MessagesSentByPerson";
	
	private static final String mailsTable = "enron";
	
	private static final String MAIL_OUT = "mail/out/msgSentByPerson/";

		
	public static class MessagesSentByPersonMapper extends
			TableMapper<Text, IntWritable> {

		private static final IntWritable one = new IntWritable(1);

		public void map(ImmutableBytesWritable row, Result value,
				Context context) throws InterruptedException, IOException {

			byte[] body = value.getValue(Bytes.toBytes("body"),
					Bytes.toBytes(""));

			if (body == null) {
				return;
			}

			InputStream input = new ByteArrayInputStream(body);
			Session s = Session.getDefaultInstance(new Properties());
			MailRecord mail;
			try {
				mail = MailRecord.create(s, input);

				String from = mail.getFrom();
				if (!from.isEmpty()) {
					context.write(new Text(from), one);
				}
			} catch (MessagingException e) {
				e.printStackTrace();
			}
		}

	}
		
	public static class MessagesSentByPersonReducer extends
			Reducer<Text, IntWritable, Text, IntWritable> {

		public void reduce(Text key, Iterable<IntWritable> values,
				Context context) throws IOException, InterruptedException {
			
			int sum = 0;
			for (IntWritable val : values) {
				sum += val.get();
			}
			context.write(key, new IntWritable(sum));
		}
	}
	
	public static void main(String args[]) throws Exception {
		if (JobRunner.run(setupJob())) {
			System.out.println("Job completed!");
		}
	}
	
	private static Job setupJob() throws IOException {
		Configuration config = HBaseConfiguration.create();
		Job job = new Job(config, jobName);
		job.setJarByClass(MessagesSentByPerson.class);

		Scan scan = new Scan();
		scan.setCaching(500);
		scan.setCacheBlocks(false); // don't set to true for MR jobs

		// Mapper
		TableMapReduceUtil.initTableMapperJob(
				mailsTable, 
				scan, 
				MessagesSentByPersonMapper.class, 
				Text.class, IntWritable.class, 
				job);

		// Reducer
		job.setCombinerClass(MessagesSentByPersonReducer.class);
		job.setReducerClass(MessagesSentByPersonReducer.class);
		job.setNumReduceTasks(1);

		FileOutputFormat.setOutputPath(job, new Path(
				MessagesSentByPerson.MAIL_OUT));

		return job;
	}
	
}