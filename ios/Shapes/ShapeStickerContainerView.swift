//  ShapeStickerContainerView.swift
//  Example
//
//  Created by long on 2020/11/20.
//

import UIKit
import ZLImageEditor

class ShapeStickerContainerView: UIView, ZLShapeStickerContainerDelegate {
    
    static let baseViewH: CGFloat = 140
    
    var baseView: UIView!
    var collectionView: UICollectionView!
    
    // MARK: - FIX: Removed @objc from this property.
    var selectShapeBlock: ((ZLShapeType) -> Void)?
    
    // This property can remain @objc as its type is compatible.
    @objc var hideBlock: (() -> Void)?
    
    // Make sure ZLShapeStickerView and its methods are public in their original files.
    let datas = ZLShapeType.allCases
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        self.setupUI()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        let path = UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: self.frame.width, height: ShapeStickerContainerView.baseViewH), byRoundingCorners: [.topLeft, .topRight], cornerRadii: CGSize(width: 8, height: 8))
        self.baseView.layer.mask = nil
        let maskLayer = CAShapeLayer()
        maskLayer.path = path.cgPath
        self.baseView.layer.mask = maskLayer
    }
    
    func setupUI() {
        self.baseView = UIView()
        self.addSubview(self.baseView)
        self.baseView.snp.makeConstraints { (make) in
            make.left.right.equalTo(self)
            make.bottom.equalTo(self.snp.bottom).offset(ShapeStickerContainerView.baseViewH)
            make.height.equalTo(ShapeStickerContainerView.baseViewH)
        }
        
        let visualView = UIVisualEffectView(effect: UIBlurEffect(style: .dark))
        self.baseView.addSubview(visualView)
        visualView.snp.makeConstraints { (make) in
            make.edges.equalTo(self.baseView)
        }
        
        let toolView = UIView()
        toolView.backgroundColor = UIColor(white: 0.4, alpha: 0.4)
        self.baseView.addSubview(toolView)
        toolView.snp.makeConstraints { (make) in
            make.top.left.right.equalTo(self.baseView)
            make.height.equalTo(0)
        }
        
        let hideBtn = UIButton(type: .custom)
        hideBtn.setImage(UIImage(named: "close"), for: .normal)
        hideBtn.backgroundColor = .clear
        hideBtn.titleLabel?.font = UIFont.systemFont(ofSize: 14)
        hideBtn.addTarget(self, action: #selector(hideBtnClick), for: .touchUpInside)
        toolView.addSubview(hideBtn)
        hideBtn.snp.makeConstraints { (make) in
            make.centerY.equalTo(toolView)
            make.right.equalTo(toolView).offset(-20)
            make.size.equalTo(CGSize(width: 40, height: 40))
        }
        
        let layout = UICollectionViewFlowLayout()
        layout.scrollDirection = .vertical
        layout.sectionInset = UIEdgeInsets(top: 10, left: 10, bottom: 10, right: 10)
        layout.minimumLineSpacing = 10
        layout.minimumInteritemSpacing = 10
        self.collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        self.collectionView.backgroundColor = .black
        self.collectionView.delegate = self
        self.collectionView.dataSource = self
        self.baseView.addSubview(self.collectionView)
        self.collectionView.snp.makeConstraints { (make) in
            make.top.equalTo(toolView.snp.bottom)
            make.left.right.bottom.equalTo(self.baseView)
        }
        
        self.collectionView.register(ShapeStickerCell.self, forCellWithReuseIdentifier: NSStringFromClass(ShapeStickerCell.classForCoder()))
        
        let tap = UITapGestureRecognizer(target: self, action: #selector(hideBtnClick))
        tap.delegate = self
        self.addGestureRecognizer(tap)
    }
    
    @objc func hideBtnClick() {
        self.hide()
    }
    
    func show(in view: UIView) {
        if self.superview !== view {
            self.removeFromSuperview()
            view.addSubview(self)
            self.snp.makeConstraints { (make) in
                make.edges.equalTo(view)
            }
            view.layoutIfNeeded()
        }
        
        self.isHidden = false
        UIView.animate(withDuration: 0.25) {
            self.baseView.snp.updateConstraints { (make) in
                make.bottom.equalTo(self.snp.bottom)
            }
            view.layoutIfNeeded()
        }
    }
    
    func hide() {
        self.hideBlock?()
        
        UIView.animate(withDuration: 0.25) {
            self.baseView.snp.updateConstraints { (make) in
                make.bottom.equalTo(self.snp.bottom).offset(ShapeStickerContainerView.baseViewH)
            }
            self.superview?.layoutIfNeeded()
        } completion: { (_) in
            self.isHidden = true
        }
    }
}

extension ShapeStickerContainerView: UIGestureRecognizerDelegate {
    public override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        let location = gestureRecognizer.location(in: self)
        return !self.baseView.frame.contains(location)
    }
}

extension ShapeStickerContainerView: UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {
    
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        let column: CGFloat = 4
        let spacing: CGFloat = 20 + 10 * (column - 1)
        let w = (collectionView.frame.width - spacing) / column
        return CGSize(width: w, height: w) 
    }
    
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return self.datas.count
    }
    
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: NSStringFromClass(ShapeStickerCell.classForCoder()), for: indexPath) as! ShapeStickerCell
        let shapeType = datas[indexPath.row]
        let templateImage = ZLShapeStickerView.templateImage(for: shapeType, size: cell.bounds.size)
        
        // Use the backward-compatible tint method here for safety.
        cell.imageView.image = templateImage.zl_tinted(with: .white)
        
        return cell
    }
    
    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        let shapeType = self.datas[indexPath.row]
        self.selectShapeBlock?(shapeType)
        self.hide()
    }
}

class ShapeStickerCell: UICollectionViewCell {
    
    var imageView: UIImageView!
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        self.imageView = UIImageView()
        self.imageView.contentMode = .scaleAspectFit
        self.contentView.addSubview(self.imageView)
        self.imageView.snp.makeConstraints { (make) in
            make.edges.equalTo(self.contentView).inset(8)
        }
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

// (It's better to have it in a shared utility file or with ZLShapeStickerView).
private extension UIImage {
    func zl_tinted(with color: UIColor) -> UIImage {
        if #available(iOS 13.0, *) {
            // After tinting the template, immediately create a new version
            // with the rendering mode set to .alwaysOriginal. This "bakes in"
            // the color and stops it from being a template.
            return self.withTintColor(color).withRenderingMode(.alwaysOriginal)
        } else {
            // The fallback method for older iOS versions.
            UIGraphicsBeginImageContextWithOptions(self.size, false, self.scale)
            guard let context = UIGraphicsGetCurrentContext() else {
                UIGraphicsEndImageContext()
                return self
            }
            
            context.translateBy(x: 0, y: self.size.height)
            context.scaleBy(x: 1.0, y: -1.0)
            context.setBlendMode(.normal)
            
            let rect = CGRect(origin: .zero, size: self.size)
            context.clip(to: rect, mask: self.cgImage!)
            color.setFill()
            context.fill(rect)
            
            let newImage = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()
            
            // This explicitly tells the system to use the pixel colors, not a tint.
            return newImage?.withRenderingMode(.alwaysOriginal) ?? self
        }
    }
}
