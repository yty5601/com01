package cn.itheima;

import java.util.ArrayList;
import java.util.List;

import cn.itheima.pojo.Person;

public class testList {
	public static void main(String[] args) {
		List<Person> list=new ArrayList<Person>();
		Person p1=new Person();
		p1.setName("yty");
		p1.setAge(27);
		
		Person p2=new Person();
		p2.setName("wtw");
		p2.setAge(65);
		
		list.add(p1);
		list.add(p2);
		
		int i=1;
		//遍历中修改集合元素
		for (Person person : list) {
			person.setAddress("北环路"+i);
			i++;
		}
		System.out.println(list);
		
	}
}
