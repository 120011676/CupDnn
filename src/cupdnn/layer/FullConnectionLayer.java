package cupdnn.layer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Vector;

import cupdnn.Network;
import cupdnn.active.ReluActivationFunc;
import cupdnn.active.SigmodActivationFunc;
import cupdnn.active.TanhActivationFunc;
import cupdnn.data.Blob;
import cupdnn.data.BlobParams;
import cupdnn.util.MathFunctions;
import cupdnn.util.Task;
import cupdnn.util.ThreadPoolManager;

public class FullConnectionLayer extends Layer{
	public static final String TYPE = "FullConnectionLayer";
	private Blob w;
	private transient Blob wGradient;
	private Blob b;
	private transient Blob bGradient;
	private transient Blob z;
	private int inSize;
	private int outSize;
	
	public FullConnectionLayer(Network network){
		super(network);
	}
	
	public FullConnectionLayer(Network network,int inSize,int outSize){
		super(network);
		this.inSize = inSize;
		this.outSize = outSize;
	}
	
	@Override
	public void prepare() {
		// TODO Auto-generated method stub
		if(w==null && b==null){
			//�����ò㹫��output.get3DSize()����Ԫ��ÿ����Ԫ��ǰ����input.get3DSize()����Ԫ����
			w = new Blob(inSize,outSize);

			//��������output.getChannels()����Ԫ��ÿ����Ԫ��һ��ƫִ
			b = new Blob(outSize);


			//��ʼ��
			float[] wData = w.getData();
			float[] bData = b.getData();
			//��˹�ֲ���ʼ��w
			MathFunctions.gaussianInitData(wData);
			//������ʼ��b
			MathFunctions.constantInitData(bData, 0.001f);
		}
		assert w!=null && b!=null:"FullConnectionLayer prepare---w or b is null error";
		wGradient = new Blob(inSize,outSize);
		bGradient = new Blob(outSize);
		//z�Ǹ��м�ֵ�������ʱ��Ҫ�õ���
		z = new Blob(mNetwork.getBatch(),outSize);
	}

	@Override
	public void forward() {
		// TODO Auto-generated method stub
		Blob input = mNetwork.getDatas().get(id-1);
		Blob output = mNetwork.getDatas().get(id);
		float[] inputData = input.getData();
		float[] outputData = output.getData();
		float[] wData = w.getData();
		float[] bData = b.getData();
		float[] zData = z.getData();
		z.fillValue(0);
		Vector<Task<Object>> workers = new Vector<Task<Object>>();
		for(int n=0;n<mNetwork.getBatch();n++){
			workers.add(new Task<Object>(n) {
				@Override
			    public Object call() throws Exception {
					for(int os=0;os<outSize;os++){//�ж��ٸ��������ǰ����ж��ٸ���Ԫ
						//��ÿ����Ԫ��Ȩ�����
						for(int is=0;is<inSize;is++){
							//zData[n*output.get3DSize()+os] ��ʾһ�������еĵ�n���ĵ�os����Ԫ
							zData[n*outSize+os] += inputData[n*inSize+is]*wData[os*inSize+is];
						}
						//ƫִ
						zData[n*outSize+os] += bData[os];
						//�����
						if(activationFunc!=null){
							outputData[n*outSize+os] = activationFunc.active(zData[n*outSize+os]);
						}else {
							outputData[n*outSize+os] = zData[n*outSize+os];
						}
					}
					return null;
				}
			});
		}
		ThreadPoolManager.getInstance(mNetwork).dispatchTask(workers);
	}

	@Override
	public void backward() {
		// TODO Auto-generated method stub
		Blob inputDiff = mNetwork.getDiffs().get(id);
		Blob outputDiff = mNetwork.getDiffs().get(id-1);
		Blob input = mNetwork.getDatas().get(id-1);
		float[] inputData = input.getData();
		float[] inputDiffData = inputDiff.getData();
		float[] outputDiffData = outputDiff.getData();
		float[] wData = w.getData();
		float[] wGradientData = wGradient.getData();
		float[] bGradientData = bGradient.getData();
		float[] zData = z.getData();
		
		//update diff
		//�ȳ˼������ƫ����,���������ǰ������
		assert inputDiff.getSize()==z.getSize():"inputDiff.getSize()==z.getSize() error";
		Vector<Task<Object>> workers = new Vector<Task<Object>>();
		if(activationFunc != null){
			for(int n=0; n < mNetwork.getBatch();n++){
				workers.add(new Task<Object>(n) {
					@Override
				    public Object call() throws Exception {
						for(int ids = 0; ids < outSize; ids++){
							inputDiffData[n*outSize+ids] *= activationFunc.diffActive(zData[n*outSize+ids]);
						}
						return null;
					}
				});
			}
			ThreadPoolManager.getInstance(mNetwork).dispatchTask(workers);
		}

		wGradient.fillValue(0);
		workers.clear();
		for(int n = 0; n < mNetwork.getBatch(); n++){
			workers.add(new Task<Object>(n) {
				@Override
			    public Object call() throws Exception {
					for(int ids = 0; ids < outSize; ids++){
						for(int is = 0; is < inSize; is++){
							//�൱��һ����Ԫ������ÿһ�����ӳ˼�
							wGradientData[ids*inSize+is] += inputData[n*inSize+is] * inputDiffData[n*outSize+ids];
						}
					}
					return null;
				}
			});
		}
		ThreadPoolManager.getInstance(mNetwork).dispatchTask(workers);
		//ƽ��
		MathFunctions.dataDivConstant(wGradientData, mNetwork.getBatch());
		
		//update bias
		bGradient.fillValue(0);
		for(int n=0;n<mNetwork.getBatch();n++){
			for(int bs = 0; bs < outSize; bs++){
				bGradientData[bs] += inputDiffData[n*outSize+bs];
			}
		}

		//ƽ��
		MathFunctions.dataDivConstant(bGradientData, mNetwork.getBatch());
		
		//��󣬳��Ե�ǰ���Ȩ�غ����
		//ÿһ�����=ÿһ����Ԫ����������Ȩ�صĳ˼�
		if(id<=1)return;
		outputDiff.fillValue(0);
		workers.clear();
		for(int n = 0; n < mNetwork.getBatch();n++){
			workers.add(new Task<Object>(n) {
				@Override
			    public Object call() throws Exception {
					for(int ids = 0; ids < outSize; ids++){
						for(int ods = 0; ods < inSize; ods++){
							outputDiffData[n*inSize+ods] += inputDiffData[n*outSize+ids]*wData[ids*inSize+ods];
						}
					}
					return null;
				}
			});
		}	
		ThreadPoolManager.getInstance(mNetwork).dispatchTask(workers);
		
		mNetwork.updateW(w, wGradient);
		mNetwork.updateW(b, bGradient);

	}


	@Override
	public String getType() {
		// TODO Auto-generated method stub
		return TYPE;
	}

	@Override
	public void saveModel(ObjectOutputStream out) {
		// TODO Auto-generated method stub
		try {
			out.writeUTF(getType());
			out.writeInt(inSize);
			out.writeInt(outSize);
			out.writeObject(w);
			out.writeObject(b);
			if(activationFunc != null){
				out.writeUTF(activationFunc.getType());
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	@Override
	public void loadModel(ObjectInputStream in) {
		// TODO Auto-generated method stub
		try {
			inSize = in.readInt();
			outSize = in.readInt();
			w = (Blob) in.readObject();
			b = (Blob) in.readObject();
			String activationType = in.readUTF();
			if(activationType.equals(ReluActivationFunc.TYPE)){
				setActivationFunc(new ReluActivationFunc());
			}else if(activationType.equals(SigmodActivationFunc.TYPE)){
				setActivationFunc(new SigmodActivationFunc());
			}else if(activationType.equals(TanhActivationFunc.TYPE)){
				setActivationFunc(new TanhActivationFunc());
			}
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public Blob createOutBlob() {
		// TODO Auto-generated method stub
		return new Blob(mNetwork.getBatch(),outSize);
	}

	@Override
	public Blob createDiffBlob() {
		// TODO Auto-generated method stub
		return new Blob(mNetwork.getBatch(),outSize);
	}



}